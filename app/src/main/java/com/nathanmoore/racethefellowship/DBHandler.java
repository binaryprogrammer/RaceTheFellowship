package com.nathanmoore.racethefellowship;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.DateFormat;
import java.util.Date;

import static com.nathanmoore.racethefellowship.FeedReaderContract.SQL_CREATE_ENTRIES;
import static com.nathanmoore.racethefellowship.FeedReaderContract.SQL_DELETE_ENTRIES;

/**
 * Created by nmoor_000 on 2/13/2017.
 */

public class DBHandler extends SQLiteOpenHelper
{
	// If you change the database schema, you must increment the database version.
	public static final int DATABASE_VERSION = 3;
	public static final String DATABASE_NAME = "JourneyEvents.db";

	public DBHandler(Context context)
	{
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	public void onCreate(SQLiteDatabase db)
	{
		db.execSQL(SQL_CREATE_ENTRIES);
	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		// This database is only a cache for online data, so its upgrade policy is
		// to simply to discard the data and start over
		db.execSQL(SQL_DELETE_ENTRIES);
		onCreate(db);
	}

	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		onUpgrade(db, oldVersion, newVersion);
	}

	public void Fill()
	{
		SQLiteDatabase db = getWritableDatabase();
		DateFormat df = DateFormat.getDateInstance();

		//int foot, Date date, float distance, float percentage, String description, int imageID
		int foot = 1;
		Date date = new Date(System.currentTimeMillis());
		float distance = 0f;
		float percentage = 0f;
		String description = "It's Frodo's birthday party. We are waiting for Gandalf to arrive before we set off.";
		int imageID = 222;

		//TODO: create loop that adds data from FellowshipPathData.

		// Create a new map of values, where column names are the keys
		ContentValues values = new ContentValues();
		values.put(FeedReaderContract.FeedEntry.FOOT, foot);
		values.put(FeedReaderContract.FeedEntry.DATE, df.format(date));
		values.put(FeedReaderContract.FeedEntry.DISTANCE, distance);
		values.put(FeedReaderContract.FeedEntry.PERCENTAGE, percentage);
		values.put(FeedReaderContract.FeedEntry.DESCRIPTION, description);
		values.put(FeedReaderContract.FeedEntry.IMAGEID, imageID);


		// Insert the new row, returning the primary key value of the new row
		long newRowId = db.insert(FeedReaderContract.FeedEntry.TABLE_NAME, null, values);
	}

}
