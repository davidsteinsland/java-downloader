import java.net.*;
import java.io.*;
import java.util.*;

interface DownloadListener
{
	public void onUpdate(File file, long bytes, long bytesRead, long timeStart);
}

public class FileDownloader
{
	public static void main (String[] args)
		throws IOException
	{
		if ( args.length == 0 )
		{
			System.err.println ("Usage: FileDownloader <url>");
			System.exit(1);
		}
		
		String url = args[0];
		
		final Download download = new Download (url);
		download.addListener (new DownloadListener()
		{
			public void onUpdate (File file, long bytes, long bytesRead, long timeStart)
			{
				int percent = (int) Math.round (((double)bytesRead/bytes) * 100),
					seconds = (int) ((System.currentTimeMillis() - timeStart) / 1000);
				
				long bytesLeft = bytes - bytesRead;
				
				int kbps = 0;
				int estTime = 0;
				
				if (bytesRead > 1024)
				{
					kbps = (int) ((double) bytesRead / (System.currentTimeMillis() - timeStart));
					estTime = (int) ( ((double)bytesLeft / 1000) / kbps );
				}
				
				StringBuilder bar = new StringBuilder("[");
				for (int i = 0; i < 50; i++)
				{
					if ( i < (percent / 2))
						bar.append ("=");
					else if (i == (percent / 2))
						bar.append(">");
					else
						bar.append(" ");
				}

				bar.append("]   " + percent + " % ");
				bar.append ("(");
				bar.append ( seconds );
				bar.append (" s, ");
				bar.append ( kbps );
				bar.append (" kBs, ");
				bar.append ( estTime );
				bar.append ( " s. est.");
				bar.append (")");
				System.out.print("\r" + bar.toString());
				
				if (percent == 100)
				{
					System.out.print ("\nDone. File saved as: " + file.toString() + "\n");
				}
			}
		});
		
		new Thread(new Runnable()
		{
			public void run ()
			{
				try
				{
					download.start(new File ("files/") );
				}
				catch (IOException e)
				{
					System.err.println (e.getMessage());
				}
			}
		}).start();
	}
	
	private static class Download
	{
		private HttpURLConnection connection;
		private List<DownloadListener> listeners = new ArrayList<>();
		
		private File file;
		private long fileSize;
		private long timeStart;
		
		public Download (String url)
			throws MalformedURLException, IOException
		{
			connection = (HttpURLConnection) (new URL(url)).openConnection();
		}
		
		public final void addListener (DownloadListener l)
		{
			listeners.add(l);
		}
		
		private void updateListeners (final long progress)
		{
			final File f = file;
			final long size = fileSize,
					   time = timeStart;
			new Thread (new Runnable ()
			{
				public void run ()
				{
					for (DownloadListener l : listeners)
					{
						l.onUpdate (f, size, progress, time);
					}
				}
			}).start();
		}
		
		public void start (File directory)
			throws IOException
		{
			connection.connect();
			
			int bufferSize = 4 * 1024;
			
			try (InputStream in = new BufferedInputStream (connection.getInputStream(), bufferSize))
			{
				String disposition = connection.getHeaderField ("Content-Disposition"),
					   filename = "file";
				
				fileSize = Long.valueOf (connection.getHeaderField ("Content-Length"));
				
				if ( fileSize < 1 )
					throw new IOException ("Content-length is reported below 1");
				
				if ( disposition != null && disposition.indexOf("=") != -1 )
				{
					filename = disposition.split("=")[1];
				}
				
				file = new File (directory, filename);
				timeStart = System.currentTimeMillis();
				
				try (OutputStream out = new BufferedOutputStream (new FileOutputStream (file), bufferSize))
				{
					byte[] buf = new byte[bufferSize];
					int[] progress = new int[101];
					long read = 0;
					int len;
					while ((len = in.read(buf)) != -1)
					{
						read += len;
						int i = (int) (((double)read/fileSize) * 100);
						// call listeners every 1 % (in a separate thread)
						if ( progress[i] == 0 )
						{
							progress[i] = 1;
							updateListeners (read);
						}
						
						out.write(buf, 0, len);
					}
				}
				catch (IOException e)
				{
					throw e;
				}
			}
			catch (IOException e)
			{
				throw e;
			}
		}
	}
}