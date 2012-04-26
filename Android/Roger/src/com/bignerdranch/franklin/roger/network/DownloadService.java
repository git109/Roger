package com.bignerdranch.franklin.roger.network;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.bignerdranch.franklin.roger.Constants;

import com.bignerdranch.franklin.roger.pair.ConnectionHelper;
import com.bignerdranch.franklin.roger.pair.ServerDescription;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

public class DownloadService extends IntentService {
	private static final String TAG = "DownloadService";

    private static final int CHUNK_SIZE = 64 * 1024;
    private static final int BUFFER_SIZE = CHUNK_SIZE;

	private DownloadManager manager;

    private static class ConnectionData {
        ServerDescription desc;
        HttpURLConnection conn;
    }

    protected ConnectionData data = new ConnectionData(); 

	public DownloadService() {
		super("DownloadService");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (Constants.ACTION_CONNECT.equals(intent.getAction()) || Constants.ACTION_DISCONNECT.equals(intent.getAction())) {
			synchronized (data) {
				if (data.conn != null) {
					data.conn.disconnect();
					data.conn = null;
				}
			}
		}

		return super.onStartCommand(intent, flags, startId);
	}

    private static class ServerChangedException extends RuntimeException {
        public static final long serialVersionUID = 0l;
        public ServerChangedException() {
            super();
        }
    }

    private void validateData() {
        synchronized (data) {
            ConnectionHelper helper = ConnectionHelper.getInstance(this);
            if (!data.desc.equals(helper.getConnectedServer())) {
                throw new ServerChangedException();
            }
        }
    }

	@Override
	protected void onHandleIntent(Intent intent) {
		manager = DownloadManager.getInstance(this);
        ConnectionHelper connector = ConnectionHelper.getInstance(this);

        if (Constants.ACTION_DISCONNECT.equals(intent.getAction())) {
            // do nothing
            return;
        }

		try {
            synchronized (data) {
                data.desc = (ServerDescription)intent.getSerializableExtra(Constants.EXTRA_SERVER_DESCRIPTION);
            }
			FileDescriptor descriptor = getDescriptor();
            connector.setDownloading(data.desc);
			String filePath = getApk(descriptor.identifier);
            connector.setFinishDownload(data.desc);
			broadcastChange(filePath, descriptor.layout, descriptor.pack, descriptor.minVersion);

            // still connected, presumably
            Intent i = new Intent(this, this.getClass());
            i.setAction(Constants.ACTION_CONNECT);
            i.putExtra(Constants.EXTRA_SERVER_DESCRIPTION, data.desc);
            startService(i);
        } catch (ServerChangedException ex) {
            // should start up again soon
		} catch (IOException e) {
			Log.e(TAG, "Unable to download file", e);
            connector.setConnectionError(data.desc, e);
		}

	}

	private FileDescriptor getDescriptor() throws IOException {
        String serverAddress = data.desc.getServerAddress();
		URL remoteUrl = new URL(serverAddress);
		Log.d(TAG, "Connecting to " + serverAddress);

		InputStream input;
		synchronized (data) {
            validateData();
			data.conn = (HttpURLConnection) remoteUrl.openConnection();
			data.conn.connect();
            ConnectionHelper.getInstance(this)
                .setConnectionSuccess(data.desc);

			input = data.conn.getInputStream();
		}

		byte[] buffer = new byte[BUFFER_SIZE];
		String layoutFile = "main";
		String identifier = "";
		String pack = "";
		String minVersionText = "";
		
		while ((input.read(buffer)) > 0) {
			String data = new String(buffer);

			String response = data.substring(0, data.indexOf("--"));
			Log.d(TAG, "Got response: " + response);

			String[] values = response.split("\n");
			layoutFile = values[0];
			layoutFile = layoutFile.split("\\.")[0];
			identifier = values[1];
			pack = values[2];
			minVersionText = values[3];

			Log.d(TAG, "Layout file: " + layoutFile + " identifier " + identifier + " package: " + pack);
		}

        synchronized (data) {
            data.conn.disconnect();
            data.conn = null;
        }
        
        int minVersion = 0;
        try {
        	minVersion = Integer.parseInt(minVersionText);
        } catch (NumberFormatException e) {
        	Log.e(TAG, "Unable to parse min version from " + minVersionText, e);
        }
        

		return new FileDescriptor(identifier, pack, layoutFile, minVersion);
	}

	private String getApk(String identifier) throws IOException {
		String address = String.format(data.desc.getApkAddress(), identifier);
		URL remoteUrl = new URL(address);
		Log.d(TAG, "Connecting to " + address);

        long startTime = System.currentTimeMillis();

		String filePath = getPath();

        new File(filePath).delete();
		FileOutputStream output = getOutputStream(filePath);
        InputStream input;
        synchronized (data) {
            validateData();

            data.conn = (HttpURLConnection) remoteUrl.openConnection();
            data.conn.setChunkedStreamingMode(CHUNK_SIZE);
            data.conn.connect();
            input = data.conn.getInputStream();
            Log.d(TAG, "Content length " + data.conn.getContentLength());
            input = data.conn.getInputStream();
        }

		byte[] buffer = new byte[BUFFER_SIZE];
		int bytesRead = 0;
		int bytesWritten = 0;

		while ((bytesRead = input.read(buffer)) > 0) {
			output.write(buffer, 0, bytesRead);
			bytesWritten += bytesRead;
            Log.i(TAG, "read " + bytesRead + " " + bytesWritten + " total");
		}

        synchronized (data) {
            data.conn.disconnect();
            data.conn = null;
        }
		
		Log.d(TAG, "Wrote " + bytesWritten + " bytes in " + (System.currentTimeMillis() - startTime) + " ms");
		return filePath;
	}

	private String getPath() {
		String path = manager.getNextPath(this);
		Log.d(TAG, "Downloading file with path: " + path);
		return path;
	}

	private FileOutputStream getOutputStream(String path) throws IOException {
		File filePath = new File(path);
		filePath.createNewFile();
		return new FileOutputStream(filePath);
	}

	private void broadcastChange(String apkPath, String layoutName, String pack, int minimumVersion) {
		manager.onDownloadComplete(apkPath, layoutName, pack, minimumVersion);
	}

	private class FileDescriptor {
		public String identifier;
		public String pack;
		public String layout;
		public int minVersion;

		public FileDescriptor(String identifier, String pack, String layout, int minVersion) {
			this.identifier = identifier;
			this.pack = pack;
			this.layout = layout;
			this.minVersion = minVersion;
		}
	}
}
