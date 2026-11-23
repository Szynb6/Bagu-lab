package com.bagu.dto.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单模块沉浸通读响应")
public class ReaderVO {

    private ModuleMetaVO module;
    private List<QuestionDetailVO> questions;
}
