package com.mobitv.sprint.ngtvwidget;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.Bitmap.CompressFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.IBinder;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.RemoteViews;

public class SprintTvAppWidget extends AppWidgetProvider {
	
	/*
	 * TODO: 
	 * 			* CHECK WHAT HAPPENS IF TILE IS NOT THERE, FAIL GRACEFULLY -- should show tile_default
	 * * GET TILE SIZE DYNAMICALLY
	 * * GET PROPERTIES FROM SKU FILES
	 * * REDO SKIN
	 * * SUPPORT hdpi, mdpi, ldpi
	 * * MAKE deep links work for browse and stream
	 * * CONSTRUCT or GET marketing tile plist URL in same way the app does it
	 */
	
	private static final String identifier = "SprintTvAppWidget";	
	static Context mycontext;
	
	public static final String INTENT_TYPE = "type";	
	public static final String INTENT_PREV = "PREV";
	public static final String INTENT_NEXT = "NEXT";
	public static final String INTENT_REFRESH = "REFRESH";
	
	public static File CACHE_DIR = null;
	public static int WIDTH = 300;
	public static int HEIGHT = 400;
	
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
		Log.v(identifier, "@@@ ******************* onUpdate ***********************");
		// why is appWidgetIds > 1
		for ( int i = 0; i < appWidgetIds.length; ++i ) {
			final int id = appWidgetIds[i];
			Intent intent = new Intent(context, UpdateService.class);
			intent.setData(Uri.parse(String.valueOf(id)));
			context.startService(intent);
		}			
		mycontext = context;
		CACHE_DIR = context.getCacheDir();
		
		// right now our tile is 300/320dp wide, so we have to account for this in the image size we reqeust from the server
		Display display = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		WIDTH = display.getWidth();
		WIDTH = WIDTH - (WIDTH / 16); // tiles are 300/320 wide, so subtract a sixteenth of the width to fetch right image
		HEIGHT = display.getHeight();
	}
	
	@Override
	public void onReceive(Context ctxt, Intent intent) {
		Log.v(identifier, "@@@ ******************* onReceive ***********************");
		final String action = intent.getAction();
		Log.v(identifier, "onReceive:action=" + action);
		
		if ( INTENT_PREV.equals(action) || INTENT_NEXT.equals(action) || INTENT_REFRESH.equals(action) ) {
			Intent prevNextIntent = new Intent(ctxt, UpdateService.class);
			prevNextIntent.setAction(action);
			ctxt.startService(prevNextIntent);
		} else if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) { // v1.5 fix that doesn't call onDelete Action 
			final int appWidgetId = intent.getExtras().getInt( AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID); 
			if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) { 
				this.onDeleted(ctxt, new int[] { appWidgetId }); 
			}
		} else {
			super.onReceive(ctxt, intent);
		}
	}	
	
	public static Boolean processingPathKey = false;

	public static MarketingTile getNextMarketingTile() {	
		if ( tiles != null && tiles.size() > 0 ) {
			marketingTileIndex = (marketingTileIndex + 1) % tiles.size();
			return tiles.get(marketingTileIndex);
		}
		return null;
	}
	
	public static MarketingTile getPrevMarketingTile() {	
		if ( tiles != null && tiles.size() > 0 ) {
			marketingTileIndex = marketingTileIndex - 1;
			marketingTileIndex = marketingTileIndex < 0 ? tiles.size() - 1 : marketingTileIndex;
			return tiles.get(marketingTileIndex);
		}
		return null;
	}	
	
	public static MarketingTile getCurrentMarketingTile() {	
		if ( tiles != null && tiles.size() > 0 ) {
			marketingTileIndex = marketingTileIndex < 0 ? 0 : marketingTileIndex;
			return tiles.get(marketingTileIndex);
		}
		return null;
	}		
	public static ArrayList<MarketingTile> tiles;
	public static ArrayList<Integer> tileIds;
	// persist the image cache beyond refreshing the files.  If in a memory crisis the SoftRefrences will be collected.
	public static HashMap<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();
	public static int marketingTileIndex = 0;
	
	public static class UpdateService extends IntentService {

		public UpdateService() {
			super("SprintTvAppWidget$UpdateService");
		}
		
		@Override
		public void onHandleIntent(Intent intent) {
			buildUpdate(null, null);
		}
		
		private void buildUpdate(Intent intent, Integer startId) {			
			ComponentName me=new ComponentName(this, SprintTvAppWidget.class);
			AppWidgetManager mgr=AppWidgetManager.getInstance(this);
			mgr.updateAppWidget(me, updateDisplay(intent, startId));
			
			if (startId != null) {
				stopSelfResult(startId);
			}			
		}
		
		private RemoteViews updateDisplay(Intent intent, Integer startId) {
			Log.v(identifier, "@@@ ************UPDATING DISPLAY************");
			
			RemoteViews views = new RemoteViews(getPackageName(), R.layout.appwidget);
			MarketingTile tile;
			
			String action = intent.getAction();
			if ( INTENT_NEXT.equals(action) ) {
				tile = getNextMarketingTile();
			} else if ( INTENT_PREV.equals(action) ) {
				tile = getPrevMarketingTile();
			} else {
				tile = getCurrentMarketingTile();
			}
			if (tile == null) {
				Log.e(identifier, "ERROR!!!  why is tile == null!?");
				return null;
			}
			
			// "mobitvng://mobitvng.mobitv.com/"
			Intent sprintTvIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("mobitvng:"));
			sprintTvIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			PendingIntent mainAction = PendingIntent.getActivity(this, 0, sprintTvIntent, 0);
			views.setOnClickPendingIntent(R.id.appwidget_logo, mainAction);
			
			
			SpannableStringBuilder text = new SpannableStringBuilder();
			if ( tile != null ) {
				text.append(tile.name);
				if ( tile.description != null && tile.description.length() > 0 ) {
					text.append('\n');
					text.append(tile.description);
				}
				
				text.setSpan(new AbsoluteSizeSpan(12, true), 0, text.length(), 0);
				text.setSpan(new StyleSpan(Typeface.BOLD), 0, tile.name.length(), 0);
				
				Intent featuredIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(tile.url()));
				featuredIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				PendingIntent featuredAction = PendingIntent.getActivity(this, 0, featuredIntent, 0);
				views.setOnClickPendingIntent(R.id.appwidget_item, featuredAction);
				views.setOnClickPendingIntent(R.id.appwidget_tile, featuredAction);
			} else {
				String title = getString(R.string.default_label);
				
				text.append(title);
				text.append('\n');
				text.append(getString(R.string.default_description));
				
				text.setSpan(new AbsoluteSizeSpan(12, true), 0, text.length(), 0);
				text.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), 0);
				
				views.setOnClickPendingIntent(R.id.appwidget_item, mainAction);
			}
			views.setTextViewText(R.id.appwidget_item, text);	
				
			// if image exists in SoftReference HashMap, get it as nextImage
			Bitmap nextImage = null;
			if ( imageCache.containsKey(tile.tile_id) ) {					
				SoftReference<Bitmap> soft_ref = (SoftReference<Bitmap>) imageCache.get(tile.tile_id);
				nextImage = soft_ref != null ? soft_ref.get() : null;				
			} 
			
			// set file, for checking existence in cache, or saving file after download
			File file = new File(CACHE_DIR, tile.id + ".png");
			if (nextImage == null) {
				if (file.exists()) {					
					nextImage = BitmapFactory.decodeFile(file.getPath());
					Log.v(identifier, "Loading file from file system, disk cache.");
					views.setImageViewBitmap(R.id.appwidget_tile, nextImage);
					// store in HashMap
					SoftReference<Bitmap> nextImage_SoftRef = new SoftReference<Bitmap>(nextImage);
					imageCache.put(tile.tile_id, nextImage_SoftRef);
				} else {					
					nextImage = downloadFile(getImageURL(tile));
					if ( nextImage != null ) {
						// store in HashMap
						SoftReference<Bitmap> nextImage_SoftRef = new SoftReference<Bitmap>(nextImage);
						imageCache.put(tile.tile_id, nextImage_SoftRef);
						// store in File System
						try {							
							FileOutputStream fos = new FileOutputStream(file);
							BufferedOutputStream bos = new BufferedOutputStream(fos);
							nextImage.compress(CompressFormat.PNG, 100, bos);
							bos.flush();
							bos.close();
							fos.close();						
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}
						Log.v(identifier, "Loading file from network, then put in cache.");
						views.setImageViewBitmap(R.id.appwidget_tile, nextImage);
					} else {					
						views.setImageViewResource(R.id.appwidget_tile, R.drawable.tile_default);
					}
				}
			} else {
				Log.v(identifier, "Loading file from HashMap, heap cache.");
				views.setImageViewBitmap(R.id.appwidget_tile, nextImage);
			}
			
			
			Intent prevIntent=new Intent(this, SprintTvAppWidget.class);
			prevIntent.setAction(INTENT_PREV);
			PendingIntent pendingPrevIntent=PendingIntent.getBroadcast(this, 0, prevIntent, 0);				
			views.setOnClickPendingIntent(R.id.appwidget_prev, pendingPrevIntent);
			
			Intent nextIntent=new Intent(this, SprintTvAppWidget.class);
			nextIntent.setAction(INTENT_NEXT);
			PendingIntent pendingNextIntent=PendingIntent.getBroadcast(this, 0, nextIntent, 0);				
			views.setOnClickPendingIntent(R.id.appwidget_next, pendingNextIntent);		
			
			Intent refreshIntent=new Intent(this, SprintTvAppWidget.class);
			refreshIntent.setAction(INTENT_REFRESH);
			PendingIntent pendingRefreshIntent=PendingIntent.getBroadcast(this, 0, refreshIntent, 0);				
			views.setOnClickPendingIntent(R.id.appwidget_refresh, pendingRefreshIntent);
			
            return views;
		}
		
		//TODO: dont hardcode TILE_HOST or TILE_DARK, get it from a SKU file 
		String TILE_HOST = "http://tmobile.rest.mobitv.com/guide/v3/icon/tmobile/mobitv_3pg/4.0/tile/";
		Boolean TILE_DARK = true;
		//TODO: get this from screen dimensions and figure it out dynamically
		String TILE_SIZE = WIDTH + "x" + HEIGHT; //300x400";
		
		String getImageURL(MarketingTile tile) {
			String tileUrl = null;
			String TILE_SHADE = TILE_DARK ? ".dark" : ".light";
			tileUrl = TILE_HOST + tile.tile_id + "/" + TILE_SIZE + TILE_SHADE + ".png";
			return tileUrl;
		}
		
		Bitmap downloadFile(String fileUrl) {
			Bitmap bmImg = null;
			URL myFileUrl = null;
			try {
				myFileUrl = new URL(fileUrl);
			} catch (Exception e) {
				e.printStackTrace();
			}
			try {
				HttpURLConnection conn = (HttpURLConnection)myFileUrl.openConnection();
				conn.setDoInput(true);
				InputStream is = conn.getInputStream();
				
				bmImg = BitmapFactory.decodeStream(is);
			} catch(IOException e) {
				e.printStackTrace();
			}
			return bmImg;
		}
		
		// go through imageCacheFiles... if not in MarketingTiles tiles, then delete it.
		// clear the cache files that do not appear in the tileIds list
		public void scrubCache() {
			if ( tileIds.size() < 1 ) return;

			// optimization to prevent arrayList.contains for each id.  This hashmap makes this O(n) instead of O(n^2)
			HashMap<String, Boolean> ids = new HashMap<String, Boolean>();
			
			for (int i=0; i < tileIds.size(); i++) {
				ids.put(tileIds.toString(), true);
			}
			
			File[] imageCacheFiles = CACHE_DIR.listFiles();
			
			for (int i=0; i<imageCacheFiles.length; i++) {
				String fileName = imageCacheFiles[i].getName();
				fileName = fileName.replace(".png", "");
				
				if (!ids.containsKey(fileName)) {
					//Log.v(identifier, "Deleting Cache File, id=" + fileName);
					imageCacheFiles[i].delete();					
				} else {
					//Log.v(identifier, "Keeping Cache File, id=" + fileName);
				}
			}
		}
		
		@Override
		public void onStart(final Intent intent, final int startId) {
			final Runnable updateUI = new Runnable(){
				public void run(){
					String action = intent.getAction();
					
					if ( INTENT_PREV.equals(action) || INTENT_NEXT.equals(action) ) {
						buildUpdate(intent, startId);
					} else {
						if ( INTENT_REFRESH.equals(action) ) {
							marketingTileIndex = 0;
						}
						String serverUrl = getString(R.string.url_plist);
						try {
							tiles = new ArrayList<MarketingTile>();
							tileIds = new ArrayList<Integer>();															
							URL featuredContentUrl = new URL(serverUrl);
							HttpURLConnection httpconn = (HttpURLConnection)featuredContentUrl.openConnection();
							httpconn.connect();
							try {
								XmlPullParserFactory xppfactory = XmlPullParserFactory.newInstance();
								XmlPullParser xpp = xppfactory.newPullParser();
								xpp.setInput(httpconn.getInputStream(), "UTF-8");
	
								int event = xpp.getEventType();
								while ( event != XmlPullParser.END_DOCUMENT ) {
									switch (event) {
									case XmlPullParser.START_TAG:
										String tagName = xpp.getName();
										if ( "plist".equalsIgnoreCase(tagName) ) {										
											event = xpp.next(); // do nothing, we throw away the plist wrapper and array wrapper
											if ( "array".equalsIgnoreCase(xpp.getName()) ) {
												event = xpp.next(); // do nothing, throw away array (of dictionaries) tag											
											}
										} else if ( "dict".equalsIgnoreCase(tagName) ) {
											MarketingTile toAdd = parseMarketingTile(xpp);											
											if (toAdd.id != null) {
												Integer id = new Integer(toAdd.id);											
												if ( id != null && !tileIds.contains(id) ) {
													tiles.add(toAdd);
													tileIds.add(new Integer(toAdd.id));
												}					
											}						
										}
										break;
	
									case XmlPullParser.END_TAG:
										break;
	
									default:
										break;
									}
									event = xpp.next();
								}
							} catch ( Exception e ) {
								Log.e(identifier, "exception occurred while parsing xml", e);
							}
							finally {
								httpconn.disconnect();
							}
						} catch (Exception ioe) {
							Log.e(identifier, "network error", ioe);
						}			
						
						scrubCache();
						
						buildUpdate(intent, startId);
					}
				}
			};		
			ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
			if( cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected() ){
				new Thread(updateUI).start();
			}
			else{
				BroadcastReceiver networkChange = new BroadcastReceiver(){
					@Override
					public void onReceive(Context context, Intent intent) {
						try{
							NetworkInfo ni = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
							if(ni != null && ni.isConnected()){
								new Thread(updateUI).start();
								UpdateService.this.unregisterReceiver(this); 
							}
						}catch( Exception e ){
							Log.e(identifier, "receiver error", e);
						}
					}
				};
				registerReceiver(networkChange, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
			}
		}
		
		public MarketingTile parseMarketingTile(XmlPullParser xpp) throws XmlPullParserException {
			MarketingTile tile = new MarketingTile();
			String currentKeyType = null;
			while(true) {
				try {
					int eventType = xpp.getEventType();
					if (eventType == XmlPullParser.START_TAG) {
						String tagName = xpp.getName();
						if ( "key".equalsIgnoreCase(tagName) ) {
							currentKeyType = xpp.nextText();
							if ( "path".equalsIgnoreCase(currentKeyType) ) {
								processingPathKey = true;
							}
						} else if ( "string".equalsIgnoreCase(tagName) ) {
							if ( "id".equalsIgnoreCase(currentKeyType) ) {
								if ( processingPathKey == true ) {
									tile.path.id = xpp.nextText();
								} else {
									tile.id = xpp.nextText();
								}
							} else if ( "media_type".equalsIgnoreCase(currentKeyType) ) {
								tile.media_type = xpp.nextText();
							} else if ( "packages".equalsIgnoreCase(currentKeyType) ) {
								tile.packages.add(xpp.nextText());
							} else if ( "name".equalsIgnoreCase(currentKeyType) ) {
								if ( processingPathKey == true ) {
									tile.path.name = xpp.nextText();
								} else {
									tile.name = xpp.nextText();
								}
							} else if ( "description".equalsIgnoreCase(currentKeyType) ) {
								tile.description = xpp.nextText();
							} else if ( "duration".equalsIgnoreCase(currentKeyType) ) {
								tile.duration = xpp.nextText();
							} else if ( "media_id".equalsIgnoreCase(currentKeyType) ) {
								tile.media_id = xpp.nextText();
							} else if ( "media_class".equalsIgnoreCase(currentKeyType) ) {
								tile.media_class = xpp.nextText();
							} else if ( "network".equalsIgnoreCase(currentKeyType) ) {
								tile.network = xpp.nextText();
							} else if ( "media_aspect_ratio".equalsIgnoreCase(currentKeyType) ) {
								tile.media_aspect_ratio = xpp.nextText();
							} else if ( "media_restrictions".equalsIgnoreCase(currentKeyType) ) {
								tile.media_restrictions.add(xpp.nextText());
							} else if ( "tile_id".equalsIgnoreCase(currentKeyType) ) {
								tile.tile_id = xpp.nextText();
							} else if ( "campaign_id".equalsIgnoreCase(currentKeyType) ) {
								tile.campaign_id = xpp.nextText();
							}
						} else if ( "array".equalsIgnoreCase(currentKeyType) ) {
							// ignore array tags
						} else if ( "integer".equalsIgnoreCase(currentKeyType) ) {
							if ( "number".equalsIgnoreCase(currentKeyType) ) {
								tile.number = Integer.parseInt(xpp.nextText());
							} 
						} else if ( "".equalsIgnoreCase(currentKeyType) ) {
							
						}
					} else if ( eventType == XmlPullParser.END_TAG ) {
						if ( "dict".equalsIgnoreCase(xpp.getName()) ) {
							if ( processingPathKey ) { // make sure to set processingPathKey when starting and finishing path
								processingPathKey = false;
							} else {
								break;
							}
						} else if ( "true".equalsIgnoreCase(xpp.getName()) ) {
							// TODO: HOW IS HAS PROGRAM DATA and VISIBLE FALSE REPRESENTED??? ABSENSE OR FALSE?
							if ( "has_program_data".equalsIgnoreCase(currentKeyType) ) {
								tile.has_program_data = true;
							} else if ( "visible".equalsIgnoreCase(currentKeyType) ) {
								tile.visible = true;
							}
						} else if ( "false".equalsIgnoreCase(xpp.getName()) ) {
							// TODO: HOW IS HAS PROGRAM DATA and VISIBLE FALSE REPRESENTED??? ABSENSE OR FALSE?
							if ( "has_program_data".equalsIgnoreCase(currentKeyType) ) {
								tile.has_program_data = false;
							} else if ( "visible".equalsIgnoreCase(currentKeyType) ) {
								tile.visible = false;
							}
						}
					}
					xpp.next();
				} catch(IOException ioe) {
					ioe.printStackTrace();
				}				
			}			
			//tile.printTile();
			return tile;
		}
		
		@Override
		public IBinder onBind(Intent intent) {
			return null;
		}
	}
}
