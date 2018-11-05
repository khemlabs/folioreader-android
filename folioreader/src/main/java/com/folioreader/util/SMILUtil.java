package com.folioreader.util;

import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONObject;
import org.readium.r2.shared.MediaOverlayNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class SMILUtil extends AsyncTask<String, Void, JSONObject> {

	private Exception exception;

//	public static String parseNodeAudio(MediaOverlayNode node){
//		String audio = node.getAudio().
//	}

	@Override
	public JSONObject doInBackground(String... uri) {
		StringBuilder response = new StringBuilder();
		try{
			URL website = new URL(uri[0]);
			URLConnection connection = website.openConnection();
			BufferedReader in = new BufferedReader( new InputStreamReader(connection.getInputStream(),"UTF8"));

			String inputLine;

			while ((inputLine = in.readLine()) != null)
				response.append(inputLine);

			in.close();

			return new JSONObject(response.toString());
		}catch (Exception e){
			exception = e;
			return new JSONObject();
		}
	}

}
