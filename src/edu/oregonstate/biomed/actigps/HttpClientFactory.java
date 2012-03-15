package edu.oregonstate.biomed.actigps;

import org.apache.http.HttpVersion;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

public class HttpClientFactory {

    private static DefaultHttpClient client;

    /**
     * Factory for an HttpClient that maintains connections in a thread safe way.
     * See: http://foo.jasonhudgins.com/2009/08/http-connection-reuse-in-android.html
     * @return Thread-Safe HttpClient
     */
    public synchronized static DefaultHttpClient getThreadSafeClient() {
        
        /* create connection manager to handle multiple connections from different threads */
        if (client == null)
		{
			HttpParams params = new BasicHttpParams();
	        ConnManagerParams.setMaxTotalConnections(params, 100);
	        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(10));
	        ConnManagerParams.setTimeout(params, 60000);
	        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);

	        // Create and initialize scheme registry 
	        SchemeRegistry schemeRegistry = new SchemeRegistry();
	        schemeRegistry.register(
	                new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

	        // Create an HttpClient with the ThreadSafeClientConnManager.
	        // This connection manager must be used if more than one thread will
	        // be using the HttpClient.
	        ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);

	        /* create new httpclient object with thread safe conn manager */
	        client = new DefaultHttpClient(cm, params);
		}

        return client;
    } 
}