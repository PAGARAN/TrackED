package com.example.tracked.api;

import android.os.AsyncTask;
import android.util.Log;
import android.util.Xml;

import com.example.tracked.model.NewsArticle;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RssFeedService {
    private static final String TAG = "RssFeedService";

    public interface RssFeedCallback {
        void onSuccess(List<NewsArticle> newsArticles);
        void onFailure(Exception e);
    }

    public void fetchFeed(String feedUrl, RssFeedCallback callback) {
        new FetchFeedTask(callback).execute(feedUrl);
    }

    private static class FetchFeedTask extends AsyncTask<String, Void, List<NewsArticle>> {
        private RssFeedCallback callback;
        private Exception exception;
        private static final String ns = null;

        FetchFeedTask(RssFeedCallback callback) {
            this.callback = callback;
        }

        @Override
        protected List<NewsArticle> doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                conn.connect();
                
                InputStream stream = conn.getInputStream();
                List<NewsArticle> articles = parseXml(stream);
                stream.close();
                
                return articles;
            } catch (Exception e) {
                Log.e(TAG, "Error fetching RSS feed", e);
                exception = e;
                return null;
            }
        }

        private List<NewsArticle> parseXml(InputStream in) throws XmlPullParserException, IOException {
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(in, null);
                parser.nextTag();
                return readFeed(parser);
            } finally {
                in.close();
            }
        }

        private List<NewsArticle> readFeed(XmlPullParser parser) throws XmlPullParserException, IOException {
            List<NewsArticle> entries = new ArrayList<>();
            
            parser.require(XmlPullParser.START_TAG, ns, "rss");
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = parser.getName();
                if (name.equals("channel")) {
                    readChannel(parser, entries);
                } else {
                    skip(parser);
                }
            }
            return entries;
        }

        private void readChannel(XmlPullParser parser, List<NewsArticle> entries) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, ns, "channel");
            
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = parser.getName();
                if (name.equals("item")) {
                    entries.add(readItem(parser));
                } else {
                    skip(parser);
                }
            }
        }

        private NewsArticle readItem(XmlPullParser parser) throws IOException, XmlPullParserException {
            parser.require(XmlPullParser.START_TAG, ns, "item");
            
            NewsArticle article = new NewsArticle();
            String content = null;
            
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                
                String name = parser.getName();
                switch (name) {
                    case "title":
                        article.setTitle(readText(parser, "title"));
                        break;
                    case "link":
                        article.setUrl(readText(parser, "link"));
                        break;
                    case "description":
                        article.setDescription(readText(parser, "description"));
                        break;
                    case "pubDate":
                        article.setPublishedAt(readText(parser, "pubDate"));
                        break;
                    case "content:encoded":
                    case "encoded":
                        content = readText(parser, parser.getName());
                        break;
                    case "media:content":
                    case "enclosure":
                        String mediaUrl = parser.getAttributeValue(null, "url");
                        if (mediaUrl != null && article.getUrlToImage() == null) {
                            article.setUrlToImage(mediaUrl);
                        }
                        skip(parser);
                        break;
                    default:
                        skip(parser);
                        break;
                }
            }
            
            // If we have content and no image yet, try to extract from content
            if (content != null && article.getUrlToImage() == null) {
                article.setUrlToImage(extractImageFromContent(content));
            }
            
            // If we still have no image and have a description, try that
            if (article.getUrlToImage() == null && article.getDescription() != null) {
                article.setUrlToImage(extractImageFromContent(article.getDescription()));
            }
            
            return article;
        }

        private String readText(XmlPullParser parser, String tag) throws IOException, XmlPullParserException {
            parser.require(XmlPullParser.START_TAG, ns, tag);
            String result = "";
            if (parser.next() == XmlPullParser.TEXT) {
                result = parser.getText();
                parser.nextTag();
            }
            parser.require(XmlPullParser.END_TAG, ns, tag);
            return result;
        }

        private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                throw new IllegalStateException();
            }
            int depth = 1;
            while (depth != 0) {
                switch (parser.next()) {
                    case XmlPullParser.END_TAG:
                        depth--;
                        break;
                    case XmlPullParser.START_TAG:
                        depth++;
                        break;
                }
            }
        }

        private String extractImageFromContent(String content) {
            if (content == null) return null;
            
            // Simple regex to find image URL in HTML content
            Pattern pattern = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<NewsArticle> articles) {
            if (articles != null) {
                callback.onSuccess(articles);
            } else {
                callback.onFailure(exception);
            }
        }
    }
}
