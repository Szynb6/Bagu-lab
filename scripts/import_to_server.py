"""
将 docx_to_json.py 解析出的 9 大模块（375 题 + 102 张原理图）幂等导入：
  1. PostgreSQL 17 (bagu_db): kb_module / kb_chapter / kb_question（ON CONFLICT Upsert）
  2. RustFS (bagu-images 桶): 按 {slug}/{filename} 键名上传所有原理图并校验公开读

默认连接本机 127.0.0.1:5432 & 127.0.0.1:9000（配合 deploy/start-ssh-tunnel.ps1 使用）；
也可通过 --ssh-host 59.110.124.170 + 环境变量 SSH_PASSWORD 自动建立临时 SSH 隧道完成导入。

示例：
  $env:DB_PASSWORD="..."; $env:RUSTFS_ACCESS_KEY="..."; $env:RUSTFS_SECRET_KEY="..."
  python scripts/import_to_server.py
"""
import argparse
import datetime
import hashlib
import hmac
import json
import mimetypes
import os
import socket
import subprocess
import sys
import threading
import urllib.error
import urllib.request
from pathlib import Path

import psycopg

ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = ROOT / 'frontend' / 'public' / 'data'
IMG_DIR = ROOT / 'frontend' / 'public' / 'images'


# ---------------------------------------------------------------------------
# 可选：自动建立临时 Paramiko SSH 端口转发隧道
# ---------------------------------------------------------------------------
class SshTunnel:
    def __init__(self, host: str, port: int, user: str, password: str, forwards: list[tuple[int, int]]):
        import paramiko
        self.ssh = paramiko.SSHClient()
        self.ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self.ssh.connect(host, port=port, username=user, password=password, timeout=15)
        self.transport = self.ssh.get_transport()
        self.stop_event = threading.Event()
        self.servers: list[socket.socket] = []
        self.local_ports: dict[int, int] = {}
        for local_port, remote_port in forwards:
            bound = self._start_forward(local_port, remote_port)
            self.local_ports[remote_port] = bound

    def _start_forward(self, local_port: int, remote_port: int) -> int:
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind(('127.0.0.1', local_port))
        bound_port = srv.getsockname()[1]
        srv.listen(32)
        srv.settimeout(0.5)
        self.servers.append(srv)

        def handle(client: socket.socket):
            try:
                chan = self.transport.open_channel('direct-tcpip', ('127.0.0.1', remote_port), client.getsockname())
            except Exception:
                client.close()
                return

            def pipe(src, dst):
                try:
                    while True:
                        buf = src.recv(16384)
                        if not buf:
                            break
                        dst.sendall(buf)
                except Exception:
                    pass
                finally:
                    try:
                        dst.shutdown(socket.SHUT_WR)
                    except Exception:
                        pass

            def relay():
                t1 = threading.Thread(target=pipe, args=(client, chan), daemon=True)
                t2 = threading.Thread(target=pipe, args=(chan, client), daemon=True)
                t1.start()
                t2.start()
                t1.join()
                t2.join()
                for s in (client, chan):
                    try:
                        s.close()
                    except Exception:
                        pass

            threading.Thread(target=relay, daemon=True).start()

        def accept_loop():
            while not self.stop_event.is_set():
                try:
                    c, _ = srv.accept()
                    threading.Thread(target=handle, args=(c,), daemon=True).start()
                except socket.timeout:
                    continue
                except Exception:
                    break

        threading.Thread(target=accept_loop, daemon=True).start()
        return bound_port

    def close(self):
        self.stop_event.set()
        for srv in self.servers:
            try:
                srv.close()
            except Exception:
                pass
        self.ssh.close()


# ---------------------------------------------------------------------------
# 零依赖 AWS SigV4 S3 上传客户端（兼容 RustFS path-style）
# ---------------------------------------------------------------------------
class SimpleS3Client:
    def __init__(self, endpoint: str, access_key: str, secret_key: str, region: str = 'us-east-1'):
        self.endpoint = endpoint.rstrip('/')
        self.host = self.endpoint.split('://', 1)[1]
        self.access_key = access_key
        self.secret_key = secret_key
        self.region = region

    def put_object(self, bucket: str, key: str, data: bytes, content_type: str) -> int:
        path = f'/{bucket}/{key.lstrip("/")}'
        now = datetime.datetime.now(datetime.timezone.utc)
        amz_date = now.strftime('%Y%m%dT%H%M%SZ')
        date_stamp = now.strftime('%Y%m%d')
        payload_hash = hashlib.sha256(data).hexdigest()

        headers = {
            'content-type': content_type,
            'host': self.host,
            'x-amz-content-sha256': payload_hash,
            'x-amz-date': amz_date,
        }
        signed_keys = sorted(headers.keys())
        canonical_headers = ''.join(f'{k}:{headers[k]}\n' for k in signed_keys)
        signed_headers = ';'.join(signed_keys)
        canonical_request = f'PUT\n{path}\n\n{canonical_headers}\n{signed_headers}\n{payload_hash}'
        scope = f'{date_stamp}/{self.region}/s3/aws4_request'
        string_to_sign = f'AWS4-HMAC-SHA256\n{amz_date}\n{scope}\n{hashlib.sha256(canonical_request.encode()).hexdigest()}'

        def sign(k: bytes, msg: str) -> bytes:
            return hmac.new(k, msg.encode('utf-8'), hashlib.sha256).digest()

        k_signing = sign(sign(sign(sign(('AWS4' + self.secret_key).encode('utf-8'), date_stamp), self.region), 's3'), 'aws4_request')
        signature = hmac.new(k_signing, string_to_sign.encode('utf-8'), hashlib.sha256).hexdigest()

        auth = f'AWS4-HMAC-SHA256 Credential={self.access_key}/{scope}, SignedHeaders={signed_headers}, Signature={signature}'
        req = urllib.request.Request(f'{self.endpoint}{path}', data=data, method='PUT')
        for k, v in headers.items():
            if k != 'host':
                req.add_header(k, v)
        req.add_header('Authorization', auth)
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=30) as resp:
                    return resp.status
            except (urllib.error.URLError, ConnectionResetError, TimeoutError):
                if attempt == 2:
                    raise
        return 500


def upload_images(endpoint: str, bucket: str, access_key: str, secret_key: str) -> int:
    s3 = SimpleS3Client(endpoint, access_key, secret_key)
    files = sorted(p for p in IMG_DIR.rglob('*') if p.is_file())
    if not files:
        raise RuntimeError(f'未在 {IMG_DIR} 找到待上传图片')

    uploaded = 0
    for f in files:
        rel = f.relative_to(IMG_DIR).as_posix()
        mime, _ = mimetypes.guess_type(f.name)
        status = s3.put_object(bucket, rel, f.read_bytes(), mime or 'application/octet-stream')
        if status not in (200, 201, 204):
            raise RuntimeError(f'上传失败 {rel}: HTTP {status}')
        uploaded += 1

    # 抽检第一张图片是否支持匿名公开读
    sample_rel = files[0].relative_to(IMG_DIR).as_posix()
    sample_url = f'{endpoint.rstrip("/")}/{bucket}/{sample_rel}'
    with urllib.request.urlopen(sample_url, timeout=10) as resp:
        assert resp.status == 200 and len(resp.read()) == files[0].stat().st_size, '匿名读校验失败'
    print(f'[RustFS] 成功上传 {uploaded} 张原理图至桶 {bucket}，公开读校验通过 ({sample_rel})')
    return uploaded


def import_postgres(host: str, port: int, dbname: str, user: str, password: str):
    modules = json.loads((DATA_DIR / 'modules.json').read_text(encoding='utf-8'))

    with psycopg.connect(host=host, port=port, dbname=dbname, user=user, password=password) as conn:
        with conn.cursor() as cur:
            total_ch = 0
            total_q = 0
            for m in modules:
                cur.execute(
                    """
                    INSERT INTO kb_module (slug, title, full_title, icon, description, sprint_guide, sort_order, updated_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, NOW())
                    ON CONFLICT (slug) DO UPDATE SET
                        title = EXCLUDED.title,
                        full_title = EXCLUDED.full_title,
                        icon = EXCLUDED.icon,
                        description = EXCLUDED.description,
                        sprint_guide = EXCLUDED.sprint_guide,
                        sort_order = EXCLUDED.sort_order,
                        updated_at = NOW()
                    RETURNING id
                    """,
                    (
                        m['slug'], m['title'], m['fullTitle'], m['icon'],
                        m.get('description', ''), m.get('sprintGuide', ''), m['order'],
                    ),
                )
                module_id = cur.fetchone()[0]

                chapter_id_map: dict[str, int] = {}
                for idx, ch in enumerate(m['chapters'], 1):
                    cur.execute(
                        """
                        INSERT INTO kb_chapter (module_id, code, title, sort_order)
                        VALUES (%s, %s, %s, %s)
                        ON CONFLICT (module_id, code) DO UPDATE SET
                            title = EXCLUDED.title,
                            sort_order = EXCLUDED.sort_order
                        RETURNING id
                        """,
                        (module_id, ch['id'], ch['title'], idx),
                    )
                    chapter_id_map[ch['id']] = cur.fetchone()[0]
                    total_ch += 1

                q_list = json.loads((DATA_DIR / 'questions' / f"{m['slug']}.json").read_text(encoding='utf-8'))
                for q in q_list:
                    cur.execute(
                        """
                        INSERT INTO kb_question (
                            module_id, chapter_id, code, priority, title,
                            hints_json, answer_md, images_json, has_table, has_code,
                            sort_order, is_deleted, updated_at
                        )
                        VALUES (%s, %s, %s, %s, %s, %s::jsonb, %s, %s::jsonb, %s, %s, %s, FALSE, NOW())
                        ON CONFLICT (module_id, code) DO UPDATE SET
                            chapter_id = EXCLUDED.chapter_id,
                            priority = EXCLUDED.priority,
                            title = EXCLUDED.title,
                            hints_json = EXCLUDED.hints_json,
                            answer_md = EXCLUDED.answer_md,
                            images_json = EXCLUDED.images_json,
                            has_table = EXCLUDED.has_table,
                            has_code = EXCLUDED.has_code,
                            sort_order = EXCLUDED.sort_order,
                            is_deleted = FALSE,
                            updated_at = NOW()
                        """,
                        (
                            module_id,
                            chapter_id_map.get(q['chapterId']),
                            q['code'],
                            q['priority'],
                            q['title'],
                            json.dumps(q.get('hints', []), ensure_ascii=False),
                            q['answerMd'],
                            json.dumps(q.get('images', []), ensure_ascii=False),
                            bool(q.get('hasTable', False)),
                            bool(q.get('hasCode', False)),
                            q['order'],
                        ),
                    )
                    total_q += 1

        conn.commit()

        # 写入后统计与 pg_trgm 检索校验
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM kb_module")
            m_cnt = cur.fetchone()[0]
            cur.execute("SELECT COUNT(*) FROM kb_chapter")
            c_cnt = cur.fetchone()[0]
            cur.execute("SELECT priority, COUNT(*) FROM kb_question WHERE is_deleted = FALSE GROUP BY priority ORDER BY priority")
            pri_stats = dict(cur.fetchall())
            cur.execute("SELECT code, title FROM kb_question WHERE title ILIKE %s LIMIT 3", ('%三色标记%',))
            trgm_hits = cur.fetchall()

        print(f'[PostgreSQL] 幂等导入完成: 模块 {m_cnt} / 篇目 {c_cnt} / 题目 {sum(pri_stats.values())} ({pri_stats})')
        print(f'[PostgreSQL] pg_trgm 模糊检索抽检（三色标记）: {trgm_hits}')


def main():
    parser = argparse.ArgumentParser(description='导入 9 份八股题库至 PG17 与 RustFS')
    parser.add_argument('--reparse', action='store_true', help='先重新运行 docx_to_json.py 解析 Word')
    parser.add_argument('--ssh-host', default='', help='若未手动开 SSH 隧道，可传服务器 IP 自动建隧道')
    parser.add_argument('--ssh-user', default='root')
    parser.add_argument('--ssh-port', type=int, default=22)
    parser.add_argument('--db-host', default=os.getenv('DB_HOST', '127.0.0.1'))
    parser.add_argument('--db-port', type=int, default=int(os.getenv('DB_PORT', '5432')))
    parser.add_argument('--db-name', default=os.getenv('DB_NAME', 'bagu_db'))
    parser.add_argument('--db-user', default=os.getenv('DB_USER', 'bagu_user'))
    parser.add_argument('--rustfs-endpoint', default=os.getenv('RUSTFS_ENDPOINT', 'http://127.0.0.1:9000'))
    parser.add_argument('--rustfs-bucket', default=os.getenv('RUSTFS_BUCKET', 'bagu-images'))
    args = parser.parse_args()

    if args.reparse:
        subprocess.run([sys.executable, str(ROOT / 'scripts' / 'docx_to_json.py')], check=True)

    db_pass = os.getenv('DB_PASSWORD')
    s3_ak = os.getenv('RUSTFS_ACCESS_KEY')
    s3_sk = os.getenv('RUSTFS_SECRET_KEY')
    if not db_pass or not s3_ak or not s3_sk:
        sys.exit('请通过环境变量提供 DB_PASSWORD、RUSTFS_ACCESS_KEY、RUSTFS_SECRET_KEY')

    tunnel = None
    try:
        db_host, db_port = args.db_host, args.db_port
        s3_endpoint = args.rustfs_endpoint
        if args.ssh_host:
            ssh_pass = os.getenv('SSH_PASSWORD')
            if not ssh_pass:
                sys.exit('使用 --ssh-host 时请通过环境变量提供 SSH_PASSWORD')
            tunnel = SshTunnel(args.ssh_host, args.ssh_port, args.ssh_user, ssh_pass, [(0, 5432), (0, 9000)])
            db_host = '127.0.0.1'
            db_port = tunnel.local_ports[5432]
            s3_endpoint = f'http://127.0.0.1:{tunnel.local_ports[9000]}'
            print(f'[SSH] 临时隧道就绪: PG -> {db_port}, RustFS -> {tunnel.local_ports[9000]}')

        upload_images(s3_endpoint, args.rustfs_bucket, s3_ak, s3_sk)
        import_postgres(db_host, db_port, args.db_name, args.db_user, db_pass)
    finally:
        if tunnel:
            tunnel.close()


if __name__ == '__main__':
    main()
