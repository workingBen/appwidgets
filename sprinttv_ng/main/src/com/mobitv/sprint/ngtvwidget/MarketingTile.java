package com.mobitv.sprint.ngtvwidget;

import java.util.ArrayList;

import android.graphics.Bitmap;
import android.util.Log;

import com.mobitv.sprint.ngtvwidget.Path;


	// hold info for a marketing tile
	public class MarketingTile {
		
		private static final String identifier = "SprintTvAppWidget";	
		
		
		public String id;
		public int number;
		public String media_type;
		public Boolean has_program_data;
		public ArrayList<String> packages;
		public String name;
		public String description;
		public String duration;
		public String media_id;
		public String media_class;
		public String network;
		public String media_aspect_ratio;
		public ArrayList<String> media_restrictions;
		public Boolean visible;
		public String tile_id;
		public String campaign_id;
		public Path path;		
		
		public MarketingTile() {
			// create blank tile
			this.packages = new ArrayList<String>();
			this.media_restrictions = new ArrayList<String>();
			this.path = new Path();
		}
		
		public MarketingTile(String id, int number, String media_type, Boolean has_program_data, ArrayList<String> packages, String name, 
				String description, String duration, String media_id, String media_class, String network, String media_aspect_ratio, 
				ArrayList<String> media_restrictions, Boolean visible, String tile_id, String campaign_id, Path path) {
			this.id = id;
			this.number = number;
			this.media_type = media_type;
			this.has_program_data = has_program_data;
			this.packages = packages;
			this.name = name;
			this.description = description;
			this.duration = duration;
			this.media_id = media_id;
			this.media_class = media_class;
			this.network = network;
			this.media_aspect_ratio = media_aspect_ratio;
			this.media_restrictions = media_restrictions;
			this.visible = visible;
			this.tile_id = tile_id;
			this.campaign_id = campaign_id;
			this.path = path;
		}
		
		public String url() {
			String campaign_id = this.campaign_id != null ? this.campaign_id : "";
			return "mobitvng:" + "//mobitvng.mobitv.com/campaign/" + campaign_id;
		}
		
		public void printTile() {
			Log.v(identifier, "**********PRINTING TILE INFORMATION**********");
			Log.v(identifier, "*********************************************");
			Log.v(identifier, "TILE.id=" + this.id);
			Log.v(identifier, "TILE.number=" + this.number);
			Log.v(identifier, "TILE.media_type=" + this.media_type);
			Log.v(identifier, "TILE.has_program_data=" + this.has_program_data);
			Log.v(identifier, "TILE.packages=" + this.packages.toString());
			Log.v(identifier, "TILE.name=" + this.name);
			Log.v(identifier, "TILE.description=" + this.description);
			Log.v(identifier, "TILE.duration=" + this.duration);
			Log.v(identifier, "TILE.media_id=" + this.media_id);
			Log.v(identifier, "TILE.media_class=" + this.media_class);
			Log.v(identifier, "TILE.network=" + this.network);
			Log.v(identifier, "TILE.media_aspect_ratio=" + this.media_aspect_ratio);
			Log.v(identifier, "TILE.media_restrictions=" + this.media_restrictions.toString());
			Log.v(identifier, "TILE.visible=" + this.visible);
			Log.v(identifier, "TILE.tile_id=" + this.tile_id);
			Log.v(identifier, "TILE.campaign_id=" + this.campaign_id);
			Log.v(identifier, "TILE.path=" + this.path);
			Log.v(identifier, "*********************************************");
		}
	}