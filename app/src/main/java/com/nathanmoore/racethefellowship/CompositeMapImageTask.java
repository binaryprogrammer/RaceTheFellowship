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
import android.view.View;
import android.widget.TextView;

import com.nathanmoore.racethefellowship.subscaleview.ImageSource;
import com.nathanmoore.racethefellowship.subscaleview.SubsamplingScaleImageView;

import java.lang.reflect.Array;
import java.net.URL;

/**
 * Created by nmoor_000 on 2/14/2017.
 */

public class CompositeMapImageTask extends AsyncTask<Void, Void, Bitmap>
{
	private static final String TAG = "CompositeMapImageTask";

	private View mRootView;

	//position and rotation of each footstep graphic to be composited onto the map background.
	private Matrix[] mFootstepM = new Matrix[56];
	private float mDistance;

	private Bitmap mMap;
	private Bitmap mFeetOutline;
	private Bitmap mFeetFill;


	public CompositeMapImageTask(View root, Bitmap map, Bitmap feetOutline, Bitmap feetFill, float distance )
	{
		super();

		mRootView = root;

		mMap = map;
		mFeetOutline = feetOutline;
		mFeetFill = feetFill;

		mDistance = distance;
	}

	@Override
	protected Bitmap doInBackground(Void... params) {

		Log.i(TAG, "Composite Image of Map.");

		Bitmap feetPercent = mFeetFill.copy(mFeetFill.getConfig(), true);

		Bitmap result = Bitmap.createBitmap(mMap.getWidth(), mMap.getHeight(), Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(result);
		canvas.drawBitmap(mMap, 0f, 0f, null);

		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		ColorFilter filter = new LightingColorFilter(Color.rgb(255, 194, 31), 0);
		paint.setColorFilter(filter);

		/* CURRENTLY WE ASSUME EACH SET OF FEET IS 20 MILES. */

		//How many complete steps have we traveled so far.
		int steps = Math.round((mDistance + .99f) / 20); //add .99 then round down, this effectivly forces the number to round up
		//What percent are we through the current foot
		float percent = (mDistance % 20f) / 20f;


		//How far along are we in the 'current' feet.
		percent = Math.round((feetPercent.getHeight() * percent) + .5f); //add .5f so when we floor our number, it rounds up correctly.

		for (int x = 0; x < feetPercent.getWidth(); ++x) {
			for (int y = feetPercent.getHeight()-1; y > 0; --y)
			{
				//check how high we've gotten along the Y axis in the bitmap.
				if (y < feetPercent.getHeight() - (percent + 1))
				{
					feetPercent.setPixel(x, y, Color.TRANSPARENT);
				}
			}
		}

		//We cannot go farther than all the way.
		if (steps > mFootstepM.length)
			steps = mFootstepM.length;

		//Draw the feet on top of the map image.
		for (int i = 0; i < steps; ++i)
		{
			mFootstepM[i] = new Matrix();
			mFootstepM[i].setRotate(FellowshipPathData.rot[i], mFeetOutline.getWidth() / 2, mFeetOutline.getHeight() / 2); //rotate around the center, not a corner;

			Log.i(TAG, "foot positions are: " + (FellowshipPathData.pos[i][0] + 10) + "x " + (FellowshipPathData.pos[i][1] + 10) + "y.");
			mFootstepM[i].postTranslate(FellowshipPathData.pos[i][0] + 10, FellowshipPathData.pos[i][1] + 10);

			if (i == steps - 1)
			{
				//this is the current step, we need to draw the percentage
				canvas.drawBitmap(feetPercent, mFootstepM[i], paint);
			}
			else
			{
				canvas.drawBitmap(mFeetFill, mFootstepM[i], paint);
			}

			canvas.drawBitmap(mFeetOutline, mFootstepM[i], paint);
		}


		return result;
	}

	@Override
	protected void onPostExecute(Bitmap bitmap)
	{
		SubsamplingScaleImageView view = (SubsamplingScaleImageView) mRootView.findViewById(R.id.subSampleImageView);
		view.setImage(ImageSource.bitmap(bitmap));
	}

	@Override
	protected void onPreExecute() {}

	@Override
	protected void onProgressUpdate(Void... values) {}
}
