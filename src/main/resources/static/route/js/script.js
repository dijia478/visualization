// --- 配置 ---
const BACKEND_BASE_URL = 'http://localhost:8080/api/routes'; // 后端API基础URL
const MAP_CENTER = [116.397428, 39.90923]; // 默认地图中心点 (北京)
const MAP_ZOOM = 12; // 默认缩放级别

// --- 全局变量 ---
let map; // 高德地图实例
let driving; // 驾车路线规划实例
let savedRoutes = []; // 存储从后端获取的所有路线
let displayedRoutes = []; // 存储当前在地图上显示的路线覆盖物
let heatmapLayer = null; // 热力图图层实例

// --- 初始化 ---
document.addEventListener('DOMContentLoaded', function () {
    initMap();
    bindEvents();
});

function initMap() {
    map = new AMap.Map('map', {
        zoom: MAP_ZOOM,
        center: MAP_CENTER
    });

    // 初始化驾车路线规划服务
    driving = new AMap.Driving({
        map: map,
        panel: null // 不显示路线面板
    });

    // 可以添加地图点击事件来设置起点/终点
    // map.on('click', function(e) {
    //     console.log('Clicked at: ', e.lnglat);
    //     // 逻辑可以在这里添加，比如让用户选择是设为起点还是终点
    // });
}

function bindEvents() {
    document.getElementById('search-route').addEventListener('click', searchAndDisplayRoute);
    document.getElementById('save-route').addEventListener('click', saveCurrentRoute);
    document.getElementById('show-all-routes').addEventListener('click', showAllSavedRoutes);
    document.getElementById('show-heatmap').addEventListener('click', showHeatmap);
    document.getElementById('clear-map').addEventListener('click', clearMap);
}

// --- 核心功能函数 ---

async function searchAndDisplayRoute() {
    const start = document.getElementById('start-point').value.trim();
    const end = document.getElementById('end-point').value.trim();

    if (!start || !end) {
        alert('请输入起点和终点地址。');
        return;
    }

    clearMap(); // 搜索前清空地图

    driving.search(start, end, function(status, result) {
        if (status === 'complete' && result.routes && result.routes.length > 0) {
            // 路线会自动绘制在地图上
            console.log("Route found:", result);
            // 可以在这里获取路线的起点终点坐标，用于保存
            // const startPoint = result.start.location;
            // const endPoint = result.end.location;
            // currentRouteCoords = { start: startPoint, end: endPoint }; // 存储当前路线坐标
        } else {
            alert('获取路线失败，请检查地址是否正确。');
            console.error('Driving search failed:', status, result);
        }
    });
}

async function saveCurrentRoute() {
    const startName = document.getElementById('start-point').value.trim();
    const endName = document.getElementById('end-point').value.trim();

    // 注意：高德API的搜索结果是异步的，直接从输入框保存名称。
    // 如果需要精确的经纬度，需要在search回调中获取并存储。
    // 这里为了简化，我们假设用户输入的地址是准确的，并依赖高德的地理编码。
    // 更严谨的做法是在搜索成功后，从result中提取精确坐标一并保存。

    if (!startName || !endName) {
        alert('请先搜索一条路线。');
        return;
    }

    // 一个简化的方法：我们先用地理编码服务获取坐标
    const startGeocode = await geocodeAddress(startName);
    const endGeocode = await geocodeAddress(endName);

    if (!startGeocode || !endGeocode) {
        alert('无法获取起点或终点的坐标，请检查地址。');
        return;
    }

    const routeData = {
        startName: startName,
        startLng: startGeocode.lng,
        startLat: startGeocode.lat,
        endName: endName,
        endLng: endGeocode.lng,
        endLat: endGeocode.lat
    };

    try {
        const response = await fetch(`${BACKEND_BASE_URL}/save`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(routeData)
        });

        if (response.ok) {
            alert('路线保存成功！');
        } else {
            const errorText = await response.text();
            throw new Error(errorText);
        }
    } catch (error) {
        console.error('保存路线时出错:', error);
        alert('保存路线失败: ' + error.message);
    }
}

// 辅助函数：地理编码地址
function geocodeAddress(address) {
    return new Promise((resolve, reject) => {
        AMap.plugin('AMap.Geocoder', function() {
            var geocoder = new AMap.Geocoder({
                city: "全国" //城市，默认：“全国”
            });
            geocoder.getLocation(address, function(status, result) {
                if (status === 'complete' && result.geocodes.length) {
                    resolve(result.geocodes[0].location); // 返回经纬度对象{lng: ..., lat: ...}
                } else {
                    console.error('Geocode failed for address:', address, status, result);
                    resolve(null); // 解析失败
                }
            });
        });
    });
}


async function showAllSavedRoutes() {
    clearMap();
    try {
        const response = await fetch(`${BACKEND_BASE_URL}/all`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        savedRoutes = await response.json();
        console.log("Fetched routes:", savedRoutes);

        savedRoutes.forEach(route => {
            // 使用高德的步行或驾车导航来绘制路线
            // 这里使用步行导航作为示例，因为它通常能处理任意两点
            AMap.plugin('AMap.Walking', function() {
                var walking = new AMap.Walking({
                    map: map,
                    hideMarkers: true, // 隐藏起终点标记
                    autoFitView: false // 不自动调整视野
                });
                displayedRoutes.push(walking); // 存储实例以便清理

                walking.search(
                    [route.startLng, route.startLat],
                    [route.endLng, route.endLat],
                    function(status, result) {
                        if (status === 'complete' && result.routes && result.routes.length > 0) {
                            // 路线已绘制
                        } else {
                            console.warn('Failed to draw route for:', route, status, result);
                        }
                    }
                );
            });
        });
    } catch (error) {
        console.error('获取所有路线时出错:', error);
        alert('获取路线失败: ' + error.message);
    }
}


async function showHeatmap() {
    clearMap();
    if (!heatmapLayer) {
        // 初始化热力图图层
        try {
            // 加载热力图 UI 组件
            const Heatmap = await AMapUI.loadUI(['overlay/Heatmap']);

            heatmapLayer = new Heatmap(map, {
                radius: 25, // 热力点的半径
                opacity: [0, 0.8], // 透明度范围
                gradient: { // 颜色渐变
                    0.1: 'blue',
                    0.3: 'green',
                    0.5: 'yellow',
                    0.7: 'orange',
                    1.0: 'red'
                }
            });
        } catch (error) {
            console.error('Failed to load Heatmap UI:', error);
            alert('加载热力图组件失败。');
            return;
        }
    }

    try {
        const response = await fetch(`${BACKEND_BASE_URL}/all`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const routes = await response.json();

        // 准备热力图数据点 (使用起点和终点坐标)
        const heatmapData = [];
        routes.forEach(route => {
            // 可以只用起点，或终点，或都用
            heatmapData.push({ lng: route.startLng, lat: route.startLat, count: 1 });
            heatmapData.push({ lng: route.endLng, lat: route.endLat, count: 1 });
            // 或者，为了更平滑的热力图，可以添加路线上的更多点
            // 这需要后端存储或实时计算路径点，较为复杂。
            // 此处简化处理。
        });

        console.log("Heatmap data:", heatmapData);

        if (heatmapData.length > 0) {
            heatmapLayer.setDataSet({
                data: heatmapData,
                max: 10 // 最大权重，影响颜色深浅
            });
            heatmapLayer.show(); // 显示热力图
        } else {
            alert('没有数据可以生成热力图。');
        }
    } catch (error) {
        console.error('生成热力图时出错:', error);
        alert('生成热力图失败: ' + error.message);
    }
}

function clearMap() {
    // 清除驾车路线
    driving.clear();

    // 清除步行路线
    displayedRoutes.forEach(routeInstance => {
        routeInstance.clear();
    });
    displayedRoutes = [];

    // 隐藏热力图
    if (heatmapLayer) {
        heatmapLayer.hide();
    }

    // 清除可能的标记等... (根据实际添加的覆盖物调整)
    map.clearMap(); // 这会清除所有覆盖物，包括路线、标记、热力图等
    heatmapLayer = null; // 重置热力图层引用，下次显示时重新初始化
}