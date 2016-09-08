package tryto.trade.web;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;

public class Helpers {
	private static ObjectMapper objMapper = new ObjectMapper();

	public static JsonNode file2dict(String path) {
		JsonNode rootNode = null;
		URL url = Resources.getResource(path);

		try {
			String jsonStr = Resources.toString(url, Charset.forName("utf-8"));
			rootNode = objMapper.readTree(jsonStr);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return rootNode;
	}
	public static JsonNode str2dict(String jsonStr) {
		JsonNode rootNode = null;
		
		try {
			rootNode = objMapper.readTree(jsonStr);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return rootNode;
	}

	public static void main(String[] args) {
		JsonNode rootNode = Helpers.file2dict("config/ht.json");
		System.out.println(rootNode.toString());
	}

}
