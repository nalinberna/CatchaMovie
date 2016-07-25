package com.radindustries.radman.catchamovie;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.radindustries.radman.catchamovie.database.MoviesContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Vector;

/**
 * Created by radman on 7/6/16.
 */
public class GetMoviesTask extends AsyncTask<String, Void, Integer> {

    private static final String LOG_TAG = GetMoviesTask.class.getSimpleName();
    private Context context;
    private static String apiKey = "b27bf3ea7724c708e10e78138ef74f26";

    public GetMoviesTask(Context context) {
        this.context = context;
    }

    @Override
    protected Integer doInBackground(String... params) {

        if (params.length == 0) {
            return null;
        }
        String movieQuery = params[0];

        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // Will contain the raw JSON response as a string.
        String movieJsonStr = null;

        try{

            final String MOVIE_BASE_URL = "http://api.themoviedb.org/3/movie";
            final String API_QUERY_PARAM = "api_key";

            Uri builtUri = Uri.parse(MOVIE_BASE_URL).buildUpon()
                    .appendPath(movieQuery)
                    .appendQueryParameter(API_QUERY_PARAM, apiKey).build();

            //Log.v(LOG_TAG, "Built Movies URI: " + builtUri.toString());

            URL url = new URL(builtUri.toString());

            // Create the request to TMDB, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // Nothing to do.
                return 0;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                // But it does make debugging a *lot* easier if you print out the completed
                // buffer for debugging.
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                // Stream was empty.  No point in parsing.
                return 0;
            }

            movieJsonStr = buffer.toString();
            //Log.v(LOG_TAG, "Movie JSON String: " + movieJsonStr);

            inputStream.close();

        } catch(IOException e) {
            Log.e(LOG_TAG, "IOException error: ", e);
            return 0;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing reader", e);
                }
            }
        }

        //parse the data out
        try{
            getMovieData(movieJsonStr, movieQuery);
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }

        return 1;
    }

    private void getMovieData(String movieJsonStr, String movieQuery) throws JSONException {

        final String BASE_POSTER_URL = "http://image.tmdb.org/t/p/w342";
        final String MOVIE_REVIEW_AND_TRAILER_URL = "http://api.themoviedb.org/3/movie";
        final String MOVIE_TRAILER_PARAM = "videos";
        final String MOVIE_REVIEWS_PARAM = "reviews";
        final String API_QUERY_PARAM = "api_key";

        final String MDB_ID = "id";
        final String MDB_OVERVIEW = "overview";
        final String MDB_POSTER_PATH = "poster_path";
        final String MDB_TITLE = "title";
        final String MDB_VOTER_AVERAGE = "vote_average";
        final String MDB_RELEASE_DATE = "release_date";
        final String MDB_RESULTS = "results";
        final String MDB_REVIEWS = "content";
        final String MDB_REVIEW_AUTHOR = "author";
        final String MDB_TRAILER_YOUTUBE_KEY = "key";
        final String MDB_TRAILER_YOUTUBE_NAME = "name";

        HttpURLConnection connection = null;
        BufferedReader reader = null;


        try{
            JSONObject movielist = new JSONObject(movieJsonStr);
            JSONArray movies = movielist.getJSONArray(MDB_RESULTS);
            //GridItem item;

            //initialise variables
            int id;
            String overviewStr;
            String posterRawPathStr;
            String posterProperPathStr;
            String titleStr;
            String voteAvgStr;
            String releaseDateStr;
            String trailersStr;
            String reviewStr;
            Vector<ContentValues> reviewsVector;
            Vector<ContentValues> trailersVector;
            JSONObject movie;
            Vector<ContentValues> moviesVector = new Vector<>(movies.length());

            for (int i = 0; i < movies.length(); i++) {

                movie = movies.getJSONObject(i);

                //extract data
                titleStr = movie.getString(MDB_TITLE);
                id = movie.getInt(MDB_ID);
                //Log.v(LOG_TAG, titleStr + "\'s ID: " + id);
                overviewStr = movie.getString(MDB_OVERVIEW);
                posterRawPathStr = movie.getString(MDB_POSTER_PATH);
                voteAvgStr = movie.getString(MDB_VOTER_AVERAGE) + " / 10";
                releaseDateStr = formatReleaseDate(movie.getString(MDB_RELEASE_DATE));
                //Log.v(LOG_TAG, titleStr + "\'s Release date: " + releaseDateStr);

                //get the review and trailer strings for the individual movie
                Uri trailerUri = Uri.parse(MOVIE_REVIEW_AND_TRAILER_URL).buildUpon()
                        .appendPath(Integer.toString(id)).appendPath(MOVIE_TRAILER_PARAM)
                        .appendQueryParameter(API_QUERY_PARAM, apiKey).build();
                Uri reviewUri = Uri.parse(MOVIE_REVIEW_AND_TRAILER_URL).buildUpon()
                        .appendPath(Integer.toString(id)).appendPath(MOVIE_REVIEWS_PARAM)
                        .appendQueryParameter(API_QUERY_PARAM, apiKey).build();

                //Log.v(LOG_TAG, "Built trailers URI for " + titleStr + ": " + trailerUri.toString());
                //Log.v(LOG_TAG, "Built reviews URI for " + titleStr + ": " + reviewUri.toString());

                URL url = new URL(trailerUri.toString());

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) return;
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) { buffer.append(line + "\n"); }

                if (buffer.length() == 0) return;

                trailersStr = buffer.toString();
                //Log.v(LOG_TAG, titleStr +"\'s Trailers String: " + trailersStr);

                url = new URL(reviewUri.toString());

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                inputStream = connection.getInputStream();
                buffer = new StringBuffer();
                if (inputStream == null) return;
                reader = new BufferedReader(new InputStreamReader(inputStream));

                while ((line = reader.readLine()) != null) { buffer.append(line + "\n"); }

                if (buffer.length() == 0) return;

                reviewStr = buffer.toString();
                //Log.v(LOG_TAG, titleStr +"\'s Reviews String: " + reviewStr);
                inputStream.close();

                reviewsVector = getReviews(MDB_RESULTS, reviewStr,
                        MDB_REVIEWS, MDB_REVIEW_AUTHOR, id);
                trailersVector = getTrailers(MDB_RESULTS, trailersStr,
                        MDB_TRAILER_YOUTUBE_KEY, MDB_TRAILER_YOUTUBE_NAME, id);

                //give the data to the grid item
                //item = new GridItem();

                posterProperPathStr = correctPosterPath(posterRawPathStr);

                Uri posterUri = Uri.parse(BASE_POSTER_URL)
                        .buildUpon().appendPath(posterProperPathStr).build();
                //Log.v(LOG_TAG, "Movie poster Uri: " + posterUri.toString());

                String posterPath = posterUri.toString();

                ContentValues cv = new ContentValues();
                cv.put(MoviesContract.MoviesEntry.COL_MOVIE_ID, id);
                cv.put(MoviesContract.MoviesEntry.COL_MOVIE_TITLE, titleStr);
                cv.put(MoviesContract.MoviesEntry.COL_MOVIE_POSTER_URL, posterPath);
                cv.put(MoviesContract.MoviesEntry.COL_MOVIE_RELEASE_DATE, releaseDateStr);
                cv.put(MoviesContract.MoviesEntry.COL_MOVIE_USER_RATING, voteAvgStr);
                cv.put(MoviesContract.MoviesEntry.COL_MOVIE_PLOT_SYNOPSIS, overviewStr);
                cv.put(MoviesContract.MoviesEntry.COL_MOVIE_SORT_TYPE_SETTING, movieQuery);

                moviesVector.add(cv);

                if (reviewsVector.size() >= 0) {
                    ContentValues[] cvs = new ContentValues[reviewsVector.size()];
                    reviewsVector.copyInto(cvs);
                    int reviewsInserted = context.getContentResolver().bulkInsert(
                            MoviesContract.ReviewEntry.CONTENT_URI, cvs
                    );
                    Log.d(LOG_TAG, reviewsInserted + " reviews inserted into " +
                            "reviews table for " + titleStr);
                }
                if (trailersVector.size() >= 0) {
                    ContentValues[] cvs = new ContentValues[trailersVector.size()];
                    trailersVector.copyInto(cvs);
                    int trailersInserted = context.getContentResolver().bulkInsert(
                            MoviesContract.TrailerEntry.CONTENT_URI,
                            cvs
                    );
                    Log.d(LOG_TAG, trailersInserted + " trialers inserted into " +
                            "trialers table for " + titleStr);
                }

//                item.setImage(posterPath);
//                item.setTitle(titleStr);
//                item.setReleaseDate(releaseDateStr);
//                item.setUserRating(voteAvgStr);
//                item.setPlotSynopsis(overviewStr);
//                item.setMovieId(id);
//                item.setReviewArrayList(new ArrayList<Review>());
//                item.setTrailerArrayList(new ArrayList<Trailer>());
//
//                mGridData.add(item);
            }

            if (moviesVector.size() > 0) {
                ContentValues[] contentValues = new ContentValues[moviesVector.size()];
                moviesVector.copyInto(contentValues);
                int moviesInserted = context.getContentResolver().bulkInsert(
                        MoviesContract.MoviesEntry.CONTENT_URI, contentValues
                );
                Log.d(LOG_TAG, moviesInserted + " movies inserted into movies table");
            }
//            Cursor creviews = context.getContentResolver().query(
//                    MoviesContract.ReviewEntry.CONTENT_URI,
//                    null, null, null, null
//            );
//            if (creviews != null) {
//                Log.d(LOG_TAG, "There are " + creviews.getCount() + " reviews in the db");
//            }
//            Cursor ctrailers = context.getContentResolver().query(
//                    MoviesContract.TrailerEntry.CONTENT_URI,
//                    null, null, null, null
//            );
//            if (ctrailers != null) {
//                Log.d(LOG_TAG, "There are " + ctrailers.getCount() + " trailers in the db");
//            }
//            Cursor c = context.getContentResolver().query(
//                    MoviesContract.MoviesEntry.CONTENT_URI,
//                    null, null, null, null
//            );
//            if (c != null) {
//                Log.d(LOG_TAG, "There are " + c.getCount() + " movies in the db");
//            }
//
//            if (ctrailers != null && creviews != null && c != null) {
//                creviews.close();
//                ctrailers.close();
//                c.close();
//            }

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException error: ", e);
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing reader ", e);
                }
            }
        }

    }

    private String formatReleaseDate(String releaseDate) {
        String[] tokens = releaseDate.split("-");
        String month = null, day;
        switch (tokens[1]) {
            case "01": month = "Jan"; break;
            case "02": month = "Feb"; break;
            case "03": month = "Mar"; break;
            case "04": month = "Apr"; break;
            case "05": month = "May"; break;
            case "06": month = "Jun"; break;
            case "07": month = "Jul"; break;
            case "08": month = "Aug"; break;
            case "09": month = "Sep"; break;
            case "10": month = "Oct"; break;
            case "11": month = "Nov"; break;
            case "12": month = "Dec"; break;
        }
        int dayInt = Integer.parseInt(tokens[2]);
        day = Integer.toString(dayInt);
        return day + " " + month + " " + tokens[0];
    }

    private String correctPosterPath(String posterRawPath) {
        StringBuilder correctStr = new StringBuilder(posterRawPath);
        correctStr.deleteCharAt(0);
        return correctStr.toString();
    }

    private Vector<ContentValues> getReviews(String resultsKey, String JSONReviewString,
                                String content, String author, int id) throws JSONException {
        JSONObject reviews = new JSONObject(JSONReviewString);
        JSONArray results = reviews.getJSONArray(resultsKey);
        Vector<ContentValues> reviewVector = new Vector<>(results.length());
        //numOfMoviesReviewed++;
        try{
            JSONObject review;
            for (int i = 0; i < results.length(); i++) {
                review = results.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put(MoviesContract.ReviewEntry.COL_MOVIE_ID, id);
                cv.put(MoviesContract.ReviewEntry.COL_MOVIE_REVIEW_AUTHOR, review.getString(author));
                cv.put(MoviesContract.ReviewEntry.COL_MOVIE_REVIEW, review.getString(content));
                reviewVector.add(cv);
//                reviewArray.add(new Review(review.getString(content), review.getString(author)));
//                Log.d(LOG_TAG, "review" +(i+1) +" of movie"
//                        + numOfMoviesReviewed + " is: " + reviewArray[i]);
            }
            //Log.d(LOG_TAG, "reviews are " + reviewArray.length);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return reviewVector;
    }

    private Vector<ContentValues> getTrailers(String resultsKey, String JSONTrailersString,
                                           String key, String title, int id)
        throws JSONException {
        JSONObject trailers = new JSONObject(JSONTrailersString);
        JSONArray results = trailers.getJSONArray(resultsKey);
        Vector<ContentValues> trailersVector = new Vector<>(results.length());
        final String YT_BASE_URL = "https://www.youtube.com/watch";
        //numOfMoviesTrailered++;
        try {
            JSONObject keyObj;
            for (int i = 0; i < results.length(); i++) {
                keyObj = results.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put(MoviesContract.TrailerEntry.COL_MOVIE_ID, id);
                cv.put(MoviesContract.TrailerEntry.COL_MOVIE_TRAILER_NAME, keyObj.getString(title));
                cv.put(MoviesContract.TrailerEntry.COL_MOVIE_TRAILER_URL, Uri.parse(YT_BASE_URL).buildUpon()
                        .appendQueryParameter("v", keyObj.getString(key)).build().toString());
                trailersVector.add(cv);
//                trailersArray.add(new Trailer(keyObj.getString(title), Uri.parse(YT_BASE_URL).buildUpon()
//                        .appendQueryParameter("v", keyObj.getString(key)).build().toString()));
//                Log.d(LOG_TAG, "trailer" +(i+1) +" of movie"
//                        + numOfMoviesTrailered + " is: " + trailersArray.get(i).getUrl());
            }
            //Log.d(LOG_TAG, "trailers are " + trailersArray.length);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return trailersVector;
    }

}
