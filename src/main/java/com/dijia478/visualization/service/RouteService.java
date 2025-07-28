package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.RouteDTO;

import java.io.IOException;
import java.util.List;

public interface RouteService {

    void saveRoute(RouteDTO route) throws IOException;

    List<RouteDTO> loadAllRoutes() throws IOException;

}
