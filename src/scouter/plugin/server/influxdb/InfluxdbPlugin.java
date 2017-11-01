package scouter.plugin.server.influxdb;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import scouter.lang.TimeTypeEnum;
import scouter.lang.pack.PerfCounterPack;
import scouter.lang.plugin.PluginConstants;
import scouter.lang.plugin.annotation.ServerPlugin;
import scouter.lang.value.Value;
import scouter.server.ConfObserver;
import scouter.server.Configure;
import scouter.server.CounterManager;
import scouter.server.Logger;
import scouter.server.core.AgentManager;
import scouter.util.HashUtil;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class InfluxdbPlugin {
    Configure conf = Configure.getInstance();

    //Default Configuration (conf/scouter.conf)
    //plugin 사용 여부 (def : true)
    private static final String ext_plugin_influxdb_enabled = "ext_plugin_influxdb_enabled";
    //influxdb measurement명 (def : counter)
    private static final String ext_plugin_influxdb_measurement = "ext_plugin_influxdb_measurement";

    /*UDP Configuration*/
    //UDP Protocol로 Influxdb와 연동 (def : true)
    private static final String ext_plugin_influxdb_udp = "ext_plugin_influxdb_udp";
    //UDP Local ip address (def : null)
    private static final String ext_plugin_influxdb_udp_local_ip = "ext_plugin_influxdb_udp_local_ip";
    //UDP Local port number (def : 0)
    private static final String ext_plugin_influxdb_udp_local_port = "ext_plugin_influxdb_udp_local_port";
    //UDP Target ip address (def : 127.0.0.1)
    private static final String ext_plugin_influxdb_udp_target_ip = "ext_plugin_influxdb_udp_target_ip";
    //UDP Target port number (def : 8089)
    private static final String ext_plugin_influxdb_udp_target_port = "ext_plugin_influxdb_udp_target_port";

    /*HTTP Configuration*/
    //HTTP Target(Influxdb) ip address (def : 127.0.0.1)
    private static final String ext_plugin_influxdb_http_target_ip = "ext_plugin_influxdb_http_target_ip";
    //HTTP Target port number (def : 8086)
    private static final String ext_plugin_influxdb_http_target_port = "ext_plugin_influxdb_http_target_port";
    //HTTP Retention policy (def : default)
    private static final String ext_plugin_influxdb_http_retention_policy = "ext_plugin_influxdb_http_retention_policy";
    
    /*Account Configuration*/
    //ID (def : root)
    private static final String ext_plugin_influxdb_id = "ext_plugin_influxdb_id";
    //Password (def : root)
    private static final String ext_plugin_influxdb_password = "ext_plugin_influxdb_password";
    //Influxdb dbName(def : scouterCounter)
    private static final String ext_plugin_influxdb_dbName = "ext_plugin_influxdb_dbName";

    private static final String tagObjName = "obj";
    private static final String tagTimeTypeName = "timeType";
    private static final String tagObjType = "objType";
    private static final String tagObjFamily = "objFamily";

    boolean enabled = conf.getBoolean(ext_plugin_influxdb_enabled, true);

    private String measurementName = conf.getValue(ext_plugin_influxdb_measurement, "counter");

    boolean isUdp = conf.getBoolean(ext_plugin_influxdb_udp, true);
    String udpLocalIp = conf.getValue(ext_plugin_influxdb_udp_local_ip);
    int udpLocalPort = conf.getInt(ext_plugin_influxdb_udp_local_port, 0);
    String udpTargetIp = conf.getValue(ext_plugin_influxdb_udp_target_ip, "127.0.0.1");
    int udpTargetPort = conf.getInt(ext_plugin_influxdb_udp_target_port, 8089);

    UdpAgent udpAgent = null;

    String httpTargetIp = conf.getValue(ext_plugin_influxdb_http_target_ip, "127.0.0.1");
    int httpTargetPort = conf.getInt(ext_plugin_influxdb_http_target_port, 8086);
    String retentionPolicy = conf.getValue(ext_plugin_influxdb_http_retention_policy, "autogen");
    String id = conf.getValue(ext_plugin_influxdb_id, "root");
    String password = conf.getValue(ext_plugin_influxdb_password, "root");
    String dbName = conf.getValue(ext_plugin_influxdb_dbName, "scouterCounter");
    
    
    InfluxDB influx = null;

    public InfluxdbPlugin() {
        if (isUdp) {
            udpAgent = UdpAgent.getInstance();
            udpAgent.setLocalAddr(udpLocalIp, udpLocalPort);
            udpAgent.setTarget(udpTargetIp, udpTargetPort);
        } else {
            influx = InfluxDBFactory.connect("http://" + httpTargetIp + ":" + httpTargetPort, id, password);
            influx.enableBatch(200, 200, TimeUnit.MILLISECONDS);
            influx.createDatabase(dbName);
        }

        ConfObserver.put("InfluxdbPlugin", new Runnable() {
            public void run() {
                enabled = conf.getBoolean(ext_plugin_influxdb_enabled, true);
                measurementName = conf.getValue(ext_plugin_influxdb_measurement, "counter");
                boolean isUdpNew = conf.getBoolean(ext_plugin_influxdb_udp, true);
                if (isUdpNew != isUdp) {
                    isUdp = isUdpNew;
                    if (isUdp) {
                        udpAgent = UdpAgent.getInstance();
                        udpAgent.setLocalAddr(udpLocalIp, udpLocalPort);
                        udpAgent.setTarget(udpTargetIp, udpTargetPort);
                    } else {
                        influx = InfluxDBFactory.connect("http://" + httpTargetIp + ":" + httpTargetPort, id, password);
                        influx.createDatabase(dbName);
                    }
                }

                //set udp local
                String newUdpLocalIp = conf.getValue(ext_plugin_influxdb_udp_local_ip);
                int newUdpLocalPort = conf.getInt(ext_plugin_influxdb_udp_local_port, 0);
                if (!newUdpLocalIp.equals(udpLocalIp) || newUdpLocalPort != udpLocalPort) {
                    udpLocalIp = newUdpLocalIp;
                    udpLocalPort = newUdpLocalPort;
                    udpAgent.setLocalAddr(udpLocalIp, udpLocalPort);
                }

                //set udp target
                String newUdpTargetIp = conf.getValue(ext_plugin_influxdb_udp_target_ip, "127.0.0.1");
                int newUdpTargetPort = conf.getInt(ext_plugin_influxdb_udp_local_port, 8089);
                if (!newUdpTargetIp.equals(udpTargetIp) || newUdpTargetPort != udpTargetPort) {
                    udpTargetIp = newUdpTargetIp;
                    udpTargetPort = newUdpTargetPort;
                    udpAgent.setTarget(udpTargetIp, udpTargetPort);
                }

                //set http target
                String newHttpTargetIp = conf.getValue(ext_plugin_influxdb_http_target_ip, "127.0.0.1");
                int newHttpTargetPort = conf.getInt(ext_plugin_influxdb_http_target_port, 8086);
                String newId = conf.getValue(ext_plugin_influxdb_id, "root");
                String newPassword = conf.getValue(ext_plugin_influxdb_password, "root");
                String newRetentionPolicy = conf.getValue(ext_plugin_influxdb_http_retention_policy, "default");

                if (!newHttpTargetIp.equals(httpTargetIp) || newHttpTargetPort != httpTargetPort
                        || !newId.equals(id) || !newPassword.equals(password)
                        || !newRetentionPolicy.equals(retentionPolicy)) {
                    httpTargetIp = newHttpTargetIp;
                    httpTargetPort = newHttpTargetPort;
                    retentionPolicy = newRetentionPolicy;
                    id = newId;
                    password = newPassword;
                    influx = InfluxDBFactory.connect("http://" + httpTargetIp + ":" + httpTargetPort, id, password);
                }

            }
        });
    }

    @ServerPlugin(PluginConstants.PLUGIN_SERVER_COUNTER)
    public void counter(PerfCounterPack pack) {
    	
        if (!enabled) {
            return;
        }

        if(pack.timetype != TimeTypeEnum.REALTIME) {
            return;
        }

        try {
        	
            String objName = pack.objName;
            int objHash = HashUtil.hash(objName);
            String objType = AgentManager.getAgent(objHash).objType;
            String objFamily = CounterManager.getInstance().getCounterEngine().getObjectType(objType).getFamily().getName();
            Point.Builder builder = Point.measurement(measurementName)
                    .time(pack.time, TimeUnit.MILLISECONDS)
                    .tag(tagObjName, objName)
                    .tag(tagObjType, objType)
                    .tag(tagObjFamily, objFamily);

            Map<String, Value> dataMap = pack.data.toMap();
            
            for (Map.Entry<String, Value> field : dataMap.entrySet()) {
                Value valueOrigin = field.getValue();
                if (valueOrigin == null) {
                    continue;
                }
                Object value = valueOrigin.toJavaObject();
                if(!(value instanceof Number)) {
                    continue;
                }
                String key = field.getKey();
                if("time".equals(key)) {
                    continue;
                }
                builder.addField(key, (Number)value);
            }
            
            Point point = builder.build();

            if (isUdp) {
                String line = point.lineProtocol();
                udpAgent.write(line);
                //System.out.println(line);
            } else { // http
                influx.write(dbName, retentionPolicy, point);
            }

        } catch (Exception e) {
        	
        	e.printStackTrace(Logger.pw());
            if (conf._trace) {
                Logger.printStackTrace("IFLX001", e);
            } else {
                Logger.println("IFLX002", e.getMessage());
            }
        }
        
    }
}

