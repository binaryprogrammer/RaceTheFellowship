package com.nathanmoore.racethefellowship;

import android.provider.BaseColumns;

/**
 * Created by nmoor_000 on 2/13/2017.
 */

public final class FeedReaderContract
{
	//was private
	public static final String SQL_CREATE_ENTRIES = "CREATE TABLE " +
			FeedEntry.TABLE_NAME + " (" +
			FeedEntry._ID + " INTEGER PRIMARY KEY," +
			FeedEntry.FOOT + " INTEGER, " +
			FeedEntry.DATE + " DATETIME, " +
			FeedEntry.DISTANCE + " FLOAT, " +
			FeedEntry.PERCENTAGE + " FLOAT, " +
			FeedEntry.DESCRIPTION + " TEXT, " +
			FeedEntry.IMAGEID + " INTEGER)";

	//was private
	public static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + FeedEntry.TABLE_NAME;

	// To prevent someone from accidentally instantiating the contract class,
	// make the constructor private.
	private FeedReaderContract() {}

	/* Inner class that defines the table contents */
	public static class FeedEntry implements BaseColumns
	{
		public static final String TABLE_NAME = "journeyevents";

		public static final String DESCRIPTION = "description";
		public static final String DATE = "date";
		public static final String FOOT = "foot";
		public static final String DISTANCE = "distance";
		public static final String PERCENTAGE = "percentage";
		public static final String IMAGEID = "imageid";
	}
}

