package scouter.plugin.server.influxdb;

public class Main {
	public static void main(String[] args) {
		InfluxdbPlugin influx = new InfluxdbPlugin();
		influx.counter(null);
	}
}
