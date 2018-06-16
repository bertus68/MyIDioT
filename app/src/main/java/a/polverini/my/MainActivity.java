package a.polverini.my;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.graphics.drawable.shapes.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import a.polverini.my.MainActivity.DisplayActivity.*;
import android.graphics.Paint.*;
import java.nio.charset.*;
import a.polverini.my.MainActivity.*;
import java.text.*;

public class MainActivity extends Activity 
{
	private boolean verbose = true;
	private Handler handler = null;
	private WebView webView = null;
	
	public static final String EXTRA_MESSAGE = "a.polverini.my.MESSAGE";
	public static final String EXTRA_ITEM = "a.polverini.my.ITEM";
	
	public void println(String fmt, Object... args) {
		print(fmt+"<br>",args);
	}
	
	public boolean checkURL(String url) {
		try {
			HttpURLConnection connection = (HttpURLConnection)(new URL(url)).openConnection (); 
			connection.setRequestMethod ("HEAD"); 
			connection.connect () ; 
			int code = connection.getResponseCode(); 
			return (code==200);
		} catch(Exception e) {
			print(e);
		}
		return false;
	}
	
	private Item root;
	private File tmp;
	private Logger log;
	
	public static class Logger  extends PrintWriter
	{
		private File file;

		public Logger(File file) throws FileNotFoundException {
			super(new FileOutputStream(file));
			this.file = file;
		}

		public void print(String fmt, Object... args) {
			println(String.format(fmt, args));
		}

		public void print(Exception e) {
			print(e.getClass().getSimpleName()+" "+e.getMessage()+"\n");
			if(true) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				pw.close();
				String s = sw.getBuffer().toString();
				print("%s\n", s);
			}
		}
	}
	
	@Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		tmp = new File(Environment.getExternalStorageDirectory(), "tmp");

		webView = this.findViewById(R.id.text);
		webView.getSettings().setJavaScriptEnabled(true);
		webView.getSettings().setBuiltInZoomControls(true);
		webView.setWebViewClient(new MyWebViewClient());

		handler = new HtmlHandler(webView);
		print("<h1>MyIDioT v0.0.2</h1>");
		print("<p>A. Polverini (2018)</p>");
	}

	@Override
	public void onBackPressed() {
		if (webView.canGoBack()) {
			webView.goBack();
		} else {
			super.onBackPressed();
		}
	}

	public void createSubMenu(Menu menu, String submenuName, String[] submenuItems) {
		SubMenu submenu = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, submenuName);
		for(String itemName : submenuItems) {
			submenu.add(Menu.NONE, Menu.NONE, Menu.NONE, itemName);
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.LOGIN:
				authenticateDialog();
				return true;
			case R.id.DOWNLOAD:
				new DownloadTask().execute();
				return true;
			case R.id.LOAD:
				//new LoadTask().execute();
				return true;
			case R.id.DISPLAY:
				try {
					Intent intent = new Intent(this, DisplayActivity.class);
					intent.putExtra(EXTRA_ITEM, root);
					startActivity(intent);
				} catch(Exception e) {
					print(e);
				}
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	public static class DisplayActivity extends Activity {
		
		private DrawableView view;
		
		public static class Text extends ShapeDrawable {
			
			public Text(int x, int y, int w, int h, int color, final String string, final float size, final Paint.Align align) {
				super(new Shape() {
						@Override
						public void draw(Canvas canvas, Paint paint) {
							paint.setTextSize(size);
							paint.setTextAlign(align);
							canvas.drawText(string, 0, 0, paint);
						}
					});
				setBounds(x, y, x + w, y + h);
				getPaint().setColor(color);
			}
			
			public Text(int x, int y, int w, int h, int color, final String string, final float size) {
				this(x, y, w, h, color, string, size, Paint.Align.LEFT);
			}
			
		}

		public static class Line extends ShapeDrawable {
			
			public static class LineShape extends Shape
			{
				private int x1;
				private int y1;
				private int x2;
				private int y2;

				private Rect bounds;
				
				public LineShape(int x1, int y1, int x2, int y2) {
					bounds = new Rect(Math.min(x1,x2), Math.min(y1,y2), Math.max(x1,x2), Math.max(y1,y2));
					this.x1 = x1 - bounds.left;
					this.y1 = y1 - bounds.top;
					this.x2 = x2 - bounds.left;
					this.y2 = y2 - bounds.top;
				}
				
				public Rect getBounds() {
					return bounds;
				}
				
				@Override
				public void draw(Canvas canvas, Paint paint) {
					canvas.drawLine(x1, y1, x2, y2, paint);
				}
			}
			
			public Line(int x1, int y1, int x2, int y2, int color) {
				super(new LineShape(x1,y1,x2,y2));
				setBounds(((LineShape)this.getShape()).getBounds());
				getPaint().setStrokeWidth(3);
				getPaint().setColor(color);
			}
		}
		
		public static class Rectangle extends ShapeDrawable {
			public Rectangle(int x, int y, int w, int h, int color) {
				super(new RectShape());
				setBounds(x, y, x + w, y + h);
				getPaint().setColor(color);
			}
		}
		
		public static class Oval extends ShapeDrawable {
			public Oval(int x, int y, int w, int h, int color) {
				super(new OvalShape());
				setBounds(x, y, x + w, y + h);
				getPaint().setColor(color);
			}
		}
		
		public static class PathLine extends ShapeDrawable {
			public PathLine(Path path, int color) {
				super(new PathShape(path, 1.0f, 1.0f));
				setBounds(0,0,1,1);
				getPaint().setStrokeJoin(Paint.Join.ROUND);
				getPaint().setPathEffect(new CornerPathEffect(10) ); 
				getPaint().setAntiAlias(true); 
				getPaint().setStyle(Paint.Style.STROKE);
				getPaint().setStrokeWidth(3);
				getPaint().setColor(color);
			}
		}
		
		public class DrawableView extends View {

			private List<ShapeDrawable> drawables;
			private ScaleGestureDetector scaleDetector;
			private GestureDetector gestureDetector;
			
			private float scaleFactor = 1.0f;
			private float translateX  = 0.0f;
			private float translateY  = 0.0f;
			
			public DrawableView(Context context) {
				super(context);
				scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
				gestureDetector = new GestureDetector(context, new ScrollListener());
				drawables = new ArrayList<>();
			}

			public void add(ShapeDrawable drawable) {
				drawables.add(drawable);
			}

			protected void onDraw(Canvas canvas) {
				super.onDraw(canvas);
				canvas.save();
				canvas.scale(scaleFactor, scaleFactor);
				canvas.translate(translateX, translateY);
				for(ShapeDrawable drawable : drawables) {
					drawable.draw(canvas);
				}
				canvas.restore();
			}
			
			@Override
			public boolean onTouchEvent(MotionEvent ev) {
				scaleDetector.onTouchEvent(ev);
				gestureDetector.onTouchEvent(ev);
				return true;
			}
			
			private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
				@Override
				public boolean onScale(ScaleGestureDetector detector) {
					scaleFactor *= detector.getScaleFactor();
					scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f));
					invalidate();
					return true;
				}
			}
			
			private class ScrollListener extends GestureDetector.SimpleOnGestureListener {
				@Override
				public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
					translateX -= (dx/scaleFactor);
					translateY -= (dy/scaleFactor);
					invalidate();
					return true;
				}
			}
		}
		
		private File tmp;
		private Logger log;
		
		@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			tmp = new File(Environment.getExternalStorageDirectory(), "tmp");
			try {
				log = new Logger(new File(tmp, "log.txt"));
				Intent intent = getIntent();
				Item item = (Item) intent.getSerializableExtra(EXTRA_ITEM);
			} catch(Exception e) {
				if(log!=null) log.print(e);
			} finally {
				if(log!=null) log.close();
			}
			setContentView(view = new DrawableView(this));
		}
	}
	
	private class DownloadTask extends AsyncTask {

		@Override
		protected Object doInBackground(Object[] args) {
			if(args!=null && args.length>0) {
				print("downloading...<br>");
				try {
					for(Object arg : args) {
						if(arg instanceof String) {
							download((String)arg);
						}
					}
				} catch(Exception e) {
					print(e);
				}
				print("downloading completed!<br>");
			}
			return null;
		}

		@Override
		protected void onPostExecute(Object o) {
			super.onPostExecute(o);
		}

		public void download(String url) throws ConnectException {
			try {
				String dir = "download";
				String name = url.substring(url.lastIndexOf("/")+1);
				println("downloading %s ...", name);
				File file = new File(new File(tmp, dir), name);
				file.getParentFile().mkdirs();
				PrintWriter out = new PrintWriter(new FileOutputStream(file));
				BufferedReader reader = new BufferedReader(new InputStreamReader(((HttpURLConnection)(new URL(url).openConnection())).getInputStream(), Charset.forName("UTF-8")));
				String line;
				while ((line = reader.readLine()) != null) {
					out.println(line);
				}
				reader.close();
				out.close();
				println("downloading %s ... done!", url);
			} catch(Exception e) {
				if(e.getMessage().startsWith("Server returned HTTP response code: 401")) {
					print(new Error("download: UNAUTHORIZED (%s)!",url));
				} else  if(e.getMessage().startsWith("Server returned HTTP response code: 400")) {
					print(new Error("download: BAD REQUEST (%s)!",url));
				} else  if(e.getMessage().startsWith("Connection timed out: connect")) {
					throw new ConnectException(e.getMessage());
				} else {
					print(e);
				}
			}
		}
	}
	
	private class LoadTask extends AsyncTask<String, Integer, List<Item>> {
		
		@Override
		protected List<Item> doInBackground(String[] args) {
			if(args!=null && args.length>0) {
				print("loading...<br>");
				try {
					List<Item> items = new ArrayList<>();
					for(String arg : args) {
						print(" - %s<br>",arg);
						Document doc = getDocument(new File(tmp, arg).getAbsolutePath());
						items.add(parseDocument(doc));
					}
					return items;
				} catch(Exception e) {
					print(e);
				}
				print("loading completed!<br>");
			}
			return null;
		}

		@Override
		protected void onPostExecute(List<Item> items) {
			super.onPostExecute(items);
			for(Item item : items) {
				root.addChild(item);
			}
		}

		public Document getDocument(String path) {
			Document doc = null;
			try {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setIgnoringComments(false);
				factory.setIgnoringElementContentWhitespace(false);
				factory.setValidating(false);
				DocumentBuilder builder = factory.newDocumentBuilder();
				doc = builder.parse(new InputSource("file:"+path));
			} catch (Exception e) {
				print(e);
			}
			return doc;
		}

		public Item parseDocument(Document doc) {
			Element root = doc.getDocumentElement();
			String nodeName = root.getNodeName();
			Item parent = new Item();
			if(verbose) print("<b>%s</b><br>",nodeName);
			String text = root.getTextContent();
			if(text!=null && !text.isEmpty()) {
				if(verbose) print("%s<br>",text);
			}
			if (root.hasAttributes()) { 
				NamedNodeMap attributes = root.getAttributes();
				for (int i=0; i < attributes.getLength(); i++) { 
					Attr attr = (Attr)attributes.item(i); 
					if (attr != null) { 
						String key = attr.getName(); 
						String val = attr.getValue(); 
						if(verbose) print("attribute: %s=%s<br>", key, val); 
					}
				}
			}
			if(root.hasChildNodes()) {
				if(verbose) print("<ul>");
				NodeList children = root.getChildNodes();
				for(int i=0; i< children.getLength(); i++) { 
					Node child = children.item(i);
					parseNode(child, parent);
				}
				if(verbose) print("</ul>");
			}
			return null;
		}

		public void parseNode(Node node, Item parent) {
			String nodeName = node.getNodeName();
			Item item = new Item(parent, null, nodeName, null, null);
			if(verbose) print("<li>");
			if(verbose) print("<b>%s</b><br>",nodeName);
			String text = node.getTextContent();
			if(text!=null && !text.isEmpty()) {
				if(verbose) print("%s<br>",text);
			}
			if (node.hasAttributes()) { 
				NamedNodeMap attributes = node.getAttributes();
				for (int i=0; i < attributes.getLength(); i++) { 
					Attr attr = (Attr)attributes.item(i); 
					if (attr != null) { 
						String key = attr.getName(); 
						String val = attr.getValue(); 
						if(verbose) print("attribute: %s=%s<br>", key, val); 
						if(item!=null) {
							item.setProperty(key, val);
						}
					}
				}
			}
			if(node.hasChildNodes()) {
				if(verbose) print("<ul>");
				NodeList children = node.getChildNodes();
				for(int i=0; i< children.getLength(); i++) { 
					Node child = children.item(i);
					parseNode(child, item);
				}
				if(verbose) print("</ul>");
			}
			if(verbose) print("</li>");
		}
	}
	
	public static class Item implements Serializable {
		
		public static final long serialversionUID = 1L;
		
		public static String getUniqueName(Item parent, String name) {
			if(parent.getChild(name)!=null) {
				int i = 0;
				while(parent.getChild(name+"-"+i)!=null) {
					i++;
				}
				name = name+"-"+i;
			}
			return name;
		}
		
		private Item parent;
		private String type;
		private String name;
		private Properties properties = new Properties();
		private List<Item> children = new ArrayList<>();
		private Map<String, Item> index = new HashMap<>();
		
		public Item() {
		}
		
		public Item(Item parent, String type, String name) {
			this(parent, type, name, null, null);
		}
		
		public Item(Item parent, String type, String name, Properties properties, List<Item> children) {
			this.type = type;
			this.name = getUniqueName(parent, name);
			setParent(parent);
			if(properties!=null) {
				this.properties =  properties;
			}
			if(children!=null) {
				this.children = children;
				for(Item child : children) {
					index.put(child.getName(), child);
				}
			}
		}

		private void addChild(Item child) {
			children.add(child);
			if(child.getName()!=null) {
				index.put(child.getName(), child);
			}
		}

		public boolean hasChildren() {
			return children.size()>0;
		}
		
		public Item getChild(String name) {
			return index.get(name);
		}

		public List<Item> getChildren() {
			return children;
		}

		public void setChildren(List<Item> children) {
			this.children = children;
		}
		
		public Item getParent() {
			return parent;
		}
		
		public void setParent(Item parent) {
			this.parent = parent;
			if(parent!=null) {
				parent.addChild(this);
			}
		}
		
		public Item getRoot() {
			Item parent = getParent();
			while(parent.getParent()!=null) {
				parent = parent.getParent();
			}
			return parent;
		}

		public String getType() {
			return type;
		}
		
		public void setType(String type) {
			this.type = type;
		}
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public Properties getProperties() {
			return properties;
		}
		
		public void setProperties(Properties properties) {
			this.properties = properties;
		}
		
		public String getProperty(String key) {
			return properties.getProperty(key);
		}
		
		public void setProperty(String key, String value) {
			properties.setProperty(key, value);
		}
		
	}
	
	public class Warning extends Exception {
		public Warning(String fmt, Object... args) {
			super(String.format(fmt,args));
		}
	}

	public class Error extends Exception {
		public Error(String fmt, Object... args) {
			super(String.format(fmt,args));
		}
	}

	public void print(Exception e, String fmt, Object... args) {
		String s = String.format(fmt, args);
		String color = "red";
		if(e instanceof Warning) {
			color = "orange";
		}
		print("<p style='color:%s'>%s %s %s</p>", color, e.getClass().getSimpleName(), e.getMessage(), s);
		if(verbose) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			pw.close();
			String st = sw.getBuffer().toString();
			print("<pre style='color:%s'>%s</pre>", color, st);
		}
	}
	
	public void print(Exception e) {
		print(e, "");
	}

	public void print(String fmt, Object... args) {
		String s = String.format(fmt, args);
		handler.obtainMessage(1, s).sendToTarget();
	}

	public class HtmlHandler extends Handler {

		private WebView view;
		private StringBuffer html = new StringBuffer();

		public HtmlHandler(WebView view) {
			super(Looper.getMainLooper());
			this.view = view;
		}

		@Override
		public void handleMessage(Message message) {
			switch(message.what) {
				case 1:
					if(message.obj instanceof String) {
						String s = (String) message.obj;
						html.append(s);
						view.loadData(html.toString(), "text/html", "UTF-8");
					}
					break;
				default:
					break;
			}
		}
	}
	
	public class MyWebViewClient extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			view.loadUrl(url);
			return true;
		}
	}
	
	public void authenticateDialog() {
		LayoutInflater layout = LayoutInflater.from(this);
        View view = layout.inflate(R.layout.login, null);
        AlertDialog.Builder loginDialog = new AlertDialog.Builder(this);
        loginDialog.setView(view);
        final EditText user = view.findViewById(R.id.USERNAME);
        final EditText pass = view.findViewById(R.id.PASSWORD);
		loginDialog.setTitle("LOGIN");
        loginDialog.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					try {
						String password = pass.getText().toString();
						String username = user.getText().toString();
						if(username==null || password==null) { 
							Toast.makeText(MainActivity.this,"Invalid username or password", Toast.LENGTH_LONG).show();
							authenticateDialog();
							return;
						}
						authenticate(username, password);
					} catch(Exception e) {
						Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
					}
				}
			});
		loginDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {                       
					dialog.cancel();
				}
			});
		loginDialog.show();                                     
    }
	
	public void authenticate(final String user, final String pswd) {
		Authenticator.setDefault(new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(user, pswd.toCharArray());
				}
			});
	}
	
}
