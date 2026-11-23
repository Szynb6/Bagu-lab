package com.bagu.dto.study;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "热力图单日记录")
public class HeatmapItemVO {

    private String date;
    private int count;
}
