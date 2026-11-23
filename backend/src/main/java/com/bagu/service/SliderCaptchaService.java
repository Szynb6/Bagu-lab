package com.bagu.service;

import com.bagu.common.BizException;
import com.bagu.common.ErrorCode;
import com.bagu.dto.auth.SliderCaptchaVO;
import com.bagu.dto.auth.SliderVerifyRequest;
import com.bagu.dto.auth.SliderVerifyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工程手稿风自绘滑块拼图验证码服务（纯 Java 2D 内存绘图 + Redis 一次性核销）。
 */
@Service
@RequiredArgsConstructor
public class SliderCaptchaService {

    private static final String KEY_SLIDER_CHALLENGE = "bagu:auth:slider:q:";
    private static final String KEY_SLIDER_PASS = "bagu:auth:slider:pass:";

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(2);
    private static final Duration PASS_TOKEN_TTL = Duration.ofMinutes(3);

    public static final int CANVAS_W = 310;
    public static final int CANVAS_H = 150;
    private static final int BOX_SIZE = 40;
    private static final int TAB_R = 7;
    private static final int PIECE_W = BOX_SIZE + TAB_R + 3;
    private static final int PIECE_H = BOX_SIZE + TAB_R + 3;
    private static final int TOLERANCE_PX = 7;

    private final StringRedisTemplate stringRedisTemplate;

    public SliderCaptchaVO generate() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int targetX = rand.nextInt(84, CANVAS_W - PIECE_W - 14);
        int targetY = rand.nextInt(18, CANVAS_H - PIECE_H - 18);

        // 1. 绘制工程手稿几何底图
        BufferedImage bg = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawEngineeringBlueprint(g, rand);

        // 2. 构建拼图块遮罩（主体正方形 + 顶部圆突 + 右侧圆突 + 左侧内凹）
        boolean[][] mask = buildJigsawMask();
        boolean[][] border = buildBorderMask(mask);

        // 3. 抠取拼图块并加深底图缺口
        BufferedImage block = new BufferedImage(PIECE_W, PIECE_H, BufferedImage.TYPE_INT_ARGB);
        for (int px = 0; px < PIECE_W; px++) {
            for (int py = 0; py < PIECE_H; py++) {
                if (!mask[px][py]) {
                    continue;
                }
                int rgb = bg.getRGB(targetX + px, targetY + py);
                if (border[px][py]) {
                    block.setRGB(px, py, new Color(200, 75, 49, 245).getRGB());
                    bg.setRGB(targetX + px, targetY + py, new Color(180, 174, 162).getRGB());
                } else {
                    block.setRGB(px, py, (0xFF << 24) | (rgb & 0x00FFFFFF));
                    Color orig = new Color(rgb);
                    int dr = Math.max(0, (int) (orig.getRed() * 0.62));
                    int dg = Math.max(0, (int) (orig.getGreen() * 0.62));
                    int db = Math.max(0, (int) (orig.getBlue() * 0.62));
                    bg.setRGB(targetX + px, targetY + py, new Color(dr, dg, db).getRGB());
                }
            }
        }

        // 4. 绘制干扰虚影缺口（防止机器直接通过颜色暗区识别唯一缺口）
        int decoyX = targetX > 165 ? rand.nextInt(26, targetX - 56) : rand.nextInt(targetX + 56, CANVAS_W - PIECE_W - 10);
        int decoyY = rand.nextInt(18, CANVAS_H - PIECE_H - 18);
        for (int px = 0; px < PIECE_W; px++) {
            for (int py = 0; py < PIECE_H; py++) {
                if (border[px][py]) {
                    bg.setRGB(decoyX + px, decoyY + py, new Color(212, 208, 197).getRGB());
                }
            }
        }
        g.dispose();

        String captchaId = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(KEY_SLIDER_CHALLENGE + captchaId, String.valueOf(targetX), CHALLENGE_TTL);

        return SliderCaptchaVO.builder()
                .captchaId(captchaId)
                .backgroundImage(toBase64Png(bg))
                .blockImage(toBase64Png(block))
                .blockY(targetY)
                .canvasWidth(CANVAS_W)
                .canvasHeight(CANVAS_H)
                .build();
    }

    public SliderVerifyResponse verify(SliderVerifyRequest request) {
        String key = KEY_SLIDER_CHALLENGE + request.getCaptchaId().trim();
        String savedTargetX = stringRedisTemplate.opsForValue().get(key);
        // 一次性消费：不论成功失败立即销毁当前拼图挑战
        stringRedisTemplate.delete(key);

        if (!StringUtils.hasText(savedTargetX)) {
            return SliderVerifyResponse.builder().passed(false).build();
        }

        int targetX = Integer.parseInt(savedTargetX);
        int diff = Math.abs(request.getMoveX() - targetX);
        long duration = request.getDurationMs() != null ? request.getDurationMs() : 0L;

        if (diff <= TOLERANCE_PX && duration >= 100 && duration <= 25000) {
            String passToken = UUID.randomUUID().toString().replace("-", "");
            stringRedisTemplate.opsForValue().set(KEY_SLIDER_PASS + passToken, "1", PASS_TOKEN_TTL);
            return SliderVerifyResponse.builder()
                    .passed(true)
                    .captchaToken(passToken)
                    .build();
        }

        return SliderVerifyResponse.builder().passed(false).build();
    }

    public void consumeCaptchaToken(String captchaToken) {
        if (!StringUtils.hasText(captchaToken)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先完成滑块拼图人机验证");
        }
        String key = KEY_SLIDER_PASS + captchaToken.trim();
        Boolean deleted = stringRedisTemplate.delete(key);
        if (deleted == null || !deleted) {
            throw new BizException(ErrorCode.BAD_REQUEST, "人机验证已过期，请重新拖动滑块验证");
        }
    }

    private void drawEngineeringBlueprint(Graphics2D g, ThreadLocalRandom rand) {
        // 暖纸底色
        g.setColor(new Color(247, 246, 242));
        g.fillRect(0, 0, CANVAS_W, CANVAS_H);

        // 精密坐标网格
        g.setColor(new Color(229, 226, 218));
        g.setStroke(new BasicStroke(1f));
        for (int x = 0; x < CANVAS_W; x += 16) {
            g.drawLine(x, 0, x, CANVAS_H);
        }
        for (int y = 0; y < CANVAS_H; y += 16) {
            g.drawLine(0, y, CANVAS_W, y);
        }

        // 随机工程节点、架构连线与刻度环
        Color[] palette = {
                new Color(200, 75, 49, 110),   // 陶土红
                new Color(46, 111, 94, 110),   // 鼠尾草绿
                new Color(180, 83, 9, 105),    // 琥珀黄
                new Color(94, 90, 82, 110)     // 炭黑灰
        };

        for (int i = 0; i < 14; i++) {
            g.setColor(palette[i % palette.length]);
            g.setStroke(new BasicStroke(1.6f));
            int x1 = rand.nextInt(10, CANVAS_W - 20);
            int y1 = rand.nextInt(10, CANVAS_H - 20);
            int r = rand.nextInt(16, 44);
            if (i % 2 == 0) {
                g.drawOval(x1 - r / 2, y1 - r / 2, r, r);
                g.fillOval(x1 - 3, y1 - 3, 6, 6);
            } else {
                g.drawRoundRect(x1, y1, r + 12, r - 4, 6, 6);
            }
            int x2 = rand.nextInt(15, CANVAS_W - 15);
            int y2 = rand.nextInt(15, CANVAS_H - 15);
            g.drawLine(x1, y1, x2, y2);
        }

        // 工程编号水印与刻度线
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        g.setColor(new Color(115, 110, 100, 140));
        g.drawString("BAGU-SPEC // JVM-JUC-MYSQL-REDIS", 12, 18);
        g.drawString("SCALE 1:1 · SM-2 VERIFICATION", 12, CANVAS_H - 10);
    }

    private boolean[][] buildJigsawMask() {
        boolean[][] mask = new boolean[PIECE_W][PIECE_H];
        int boxX0 = 1;
        int boxY0 = TAB_R + 1;
        int boxX1 = boxX0 + BOX_SIZE;
        int boxY1 = boxY0 + BOX_SIZE;

        // 顶部凸圆圆心
        int topCx = boxX0 + BOX_SIZE / 2;
        int topCy = boxY0;
        // 右侧凸圆圆心
        int rightCx = boxX1;
        int rightCy = boxY0 + BOX_SIZE / 2;

        for (int x = 0; x < PIECE_W; x++) {
            for (int y = 0; y < PIECE_H; y++) {
                boolean inBox = x >= boxX0 && x <= boxX1 && y >= boxY0 && y <= boxY1;
                boolean inTopTab = Math.hypot(x - topCx, y - topCy) <= TAB_R;
                boolean inRightTab = Math.hypot(x - rightCx, y - rightCy) <= TAB_R;
                mask[x][y] = inBox || inTopTab || inRightTab;
            }
        }
        return mask;
    }

    private boolean[][] buildBorderMask(boolean[][] mask) {
        boolean[][] border = new boolean[PIECE_W][PIECE_H];
        for (int x = 0; x < PIECE_W; x++) {
            for (int y = 0; y < PIECE_H; y++) {
                if (!mask[x][y]) {
                    continue;
                }
                if (x <= 1 || y <= 1 || x >= PIECE_W - 2 || y >= PIECE_H - 2
                        || !mask[x - 1][y] || !mask[x + 1][y] || !mask[x][y - 1] || !mask[x][y + 1]) {
                    border[x][y] = true;
                }
            }
        }
        return border;
    }

    private String toBase64Png(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成滑块验证码图形失败");
        }
    }
}
