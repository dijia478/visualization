package com.dijia478.visualization.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteDTO {

    private String startName;

    private double startLng;

    private double startLat;

    private String endName;

    private double endLng;

    private double endLat;

}
