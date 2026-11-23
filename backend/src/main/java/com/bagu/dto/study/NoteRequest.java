package com.bagu.dto.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "保存个人口述助记请求")
public class NoteRequest {

    @Schema(description = "个人助记内容（传空串表示清空）")
    @Size(max = 2000, message = "助记备注最多 2000 字")
    private String note = "";
}
