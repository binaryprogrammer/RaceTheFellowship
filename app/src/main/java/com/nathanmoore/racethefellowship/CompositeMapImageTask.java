package com.nathanmoore.racethefellowship;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.util.Log;

import java.net.URL;

/**
 * Created by nmoor_000 on 2/14/2017.
 */

//TODO: work in progress do not use.
public class CompositeMapImageTask extends AsyncTask<URL, Integer, Long>
{
	protected Long doInBackground(URL... urls)
	{
		int count = urls.length;
		long totalSize = 0;
		for (int i = 0; i < count; i++)
		{
			//totalSize += Downloader.downloadFile(urls[i]);
			publishProgress((int) ((i / (float) count) * 100));
			// Escape early if cancel() is called
			if (isCancelled())
				break;
		}
		return totalSize;
	}

	protected void onProgressUpdate(Integer... progress)
	{
		//setProgressPercent(progress[0]);
	}

	protected void onPostExecute(Long result)
	{
		//showDialog("Downloaded " + result + " bytes");
	}

}
