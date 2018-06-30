package a.polverini.my;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.graphics.drawable.shapes.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import android.widget.*;
import android.content.SharedPreferences.*;

public class MainActivity extends Activity 
{
	// constants
	public static final String EXTRA_MESSAGE = "a.polverini.my.MESSAGE";
	public static final String EXTRA_ITEM    = "a.polverini.my.ITEM";
	public static final String NEXUS = "";
	public static final String GIT  = "";

	// preferences
	private SharedPreferences preferences;
	private boolean verbose = true;
	private File rootdir = null;
	private File dudir = null;

	// display
	private Handler handler = null;
	private WebView webView = null;
	private Menu menu;

	// data
	private Item root = new Item();

	public static final Map<String, String> deployableUnits = new HashMap<>();

	void duInit() {
		try {
			List<String> keys = new ArrayList<>();

			for(Object key : preferences.getAll().keySet()) {
				if(key instanceof String && ((String)key).startsWith("du:")) {
					keys.add(((String)key).split(":",2)[1]);
				}
			}

			for(String key : keys) {
				deployableUnits.put(key, preferences.getString("du:"+key, ""));
			}
		} catch(Exception e) {
			print(e);
		}
	}

	void duSave() {
		try {
			SharedPreferences.Editor editor = preferences.edit();
			for (String key : deployableUnits.keySet()) {
				editor.putString("du:"+key, deployableUnits.get(key));
			}
			editor.apply();
			editor.commit();
		} catch(Exception e) {
			print(e);
		}
	}

	private static Map<String, String> alias = new HashMap<>();

	private void aliasInit() {
		try {
			List<String> keys = new ArrayList<>();

			for(Object key : preferences.getAll().keySet()) {
				if(key instanceof String && ((String)key).startsWith("alias:")) {
					keys.add(((String)key).split(":",2)[1]);
				}
			}

			for(String key : keys) {
				alias.put(key, preferences.getString("alias:"+key, ""));
			}

		} catch(Exception e) {
			print(e);
		}
	}

	private void aliasSave() {
		try {
			SharedPreferences.Editor editor = preferences.edit();
			for (String key : alias.keySet()) {
				editor.putString("alias:"+key, alias.get(key));
			}
			editor.apply();
			editor.commit();
		} catch(Exception e) {
			print(e);
		}
	}

	@Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		webView = this.findViewById(R.id.text);
		webView.getSettings().setJavaScriptEnabled(true);
		webView.getSettings().setBuiltInZoomControls(true);
		webView.setWebViewClient(new MyWebViewClient());

		handler = new HtmlHandler(webView);
		print("<h1>MyIDioT v0.1.8</h1>");
		print("A. Polverini (2018)<br>");

		preferences = getPreferences(Context.MODE_PRIVATE);

		rootdir = new File(Environment.getExternalStorageDirectory(), preferences.getString("rootdir", "tmp"));

		dudir = new File(rootdir, "du");
		if(!dudir.exists()) {
			dudir.mkdirs();
		}

		aliasInit();
		aliasSave();

		duInit();
		duSave();

	}

	@Override 
	protected void onDestroy() { 
		super.onDestroy(); 
	}

	@Override
	public void onBackPressed() {
		if (webView.canGoBack()) {
			webView.goBack();
		} else {
			super.onBackPressed();
		}
	}

	void componentsMenu(Menu menu) {

		List<String> components = new ArrayList<>();
		for(Object key : preferences.getAll().keySet()) {
			if(key instanceof String && ((String)key).startsWith("du:")) {
				components.add(((String)key).substring(3));
			}
		}

		SubMenu submenu = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, "Components");
		for(String component : components) {
			submenu.add(Menu.NONE, Menu.NONE, Menu.NONE, component);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		this.menu = menu;
		componentsMenu(menu);
		return true;
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.LOGIN:
				return true;
			case R.id.DOWNLOAD:
				new DownloadTask().execute();
				return true;
			case R.id.DISPLAY:
				startDisplayActivity();
				return true;
			default:
				if(deployableUnits.containsKey(item.getTitle())) {
					String url = deployableUnits.get(item.getTitle());
					String name = url.substring(url.lastIndexOf("/")+1);
					new LoadTask().execute(name);
					return true;
				}
				return super.onOptionsItemSelected(item);
		}
	}

	public String getNexusURL(String component, String build) {
		String du = component+"."+component.substring(component.lastIndexOf('.')+1)+"DU";
		String url = NEXUS + "/" + component.replaceAll("\\.","/") + "/" + du + "/" + build + "/" + du + "-" + build + "-wiring.xml";
		return url;
	}

	public String getGitURL(String repository, String branch, String path) {
		String filename = path.replaceAll("/","!");
		return String.format("%s/%s.git/%s/%s", GIT, repository, branch, filename);
	}

	public boolean checkURL(String url) {
		try {
			HttpURLConnection connection = (HttpURLConnection)(new URL(url)).openConnection (); 
			connection.setRequestMethod ("HEAD"); 
			connection.connect () ; 
			int code = connection.getResponseCode(); 
			if(code==200) {
				return true;
			}
		} catch(Exception e) {
			print(e);
		}
		return false;
	}

	private void startDisplayActivity() {
		try {
			Intent intent = new Intent(this, DisplayActivity.class);
			intent.putExtra(EXTRA_ITEM, root);
			startActivity(intent);
		} catch(Exception e) {
			print(e);
		}
	}

	public static class PreferencesActivity extends Activity {

		@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.main);
		}

	}

	public static class DisplayActivity extends Activity {

		private Logger log;
		private DrawableView view;

		private String aliasGet(String name) {
			try {
				String key = name.substring(0, name.lastIndexOf("."));
				if(log!=null) {
					log.print("key="+key+"\n");
				}
				if(alias.containsKey(key)){
					if(log!=null) {
						log.print("yes\n");
					}
					return name.replaceFirst(key, alias.get(key));
				}
			} catch(Exception e) {
				if(log!=null) {
					log.print(e.getClass().getSimpleName()+"\n");
				}
			}
			return name;
		}

		@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			File logdir = new File(Environment.getExternalStorageDirectory(), "tmp/logs");
			if(!logdir.exists()) {
				logdir.mkdirs();
			}

			try {
				log = new Logger(new File(logdir, "log.txt"));
			} catch(Exception e) {
				Toast.makeText(this, "can't get a Logger instance!", Toast.LENGTH_LONG).show();
			}

			setContentView(view = new DrawableView(this));

			try {
				Intent intent = getIntent();
				Item item = (Item) intent.getSerializableExtra(EXTRA_ITEM);
				if(item!=null) {
					int x = 500;
					int y = 200;
					for(Item du : item.getChildren()) {
						DeployableUnit deployableUnit = new DeployableUnit(du,x, y);
						Item c = du.getChild("composition");
						if(c!=null) {
							Composition composition = new Composition(c, x+500, y+100 );
							if(composition!=null) {
								Rect b = composition.getBounds();
								y = b.bottom + 300;
								Rect duBounds = deployableUnit.getBounds();
								duBounds.bottom = b.bottom + 250;
								deployableUnit.setBounds(duBounds);
							}
							composition.wiring();
						}
					}
				}
			} catch(Exception e) {
				if(log!=null) log.print(e);
			}

			if(log!=null) log.close();
		}

		public class Text extends ShapeDrawable {

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

		public class LineShape extends Shape
		{
			private int x1;
			private int y1;
			private int x2;
			private int y2;

			private Rect bounds;

			public LineShape(int x1, int y1, int x2, int y2) {
				int minX = Math.min(x1,x2);
				int minY = Math.min(y1,y2);
				int maxX = Math.max(x1,x2);
				int maxY = Math.max(y1,y2);

				bounds = new Rect(minX, minY, maxX, maxY);

				this.x1 = x1 - minX;
				this.y1 = y1 - minY;
				this.x2 = x2 - minX;
				this.y2 = y2 - minY;
			}

			public Rect getBounds() {
				return bounds;
			}

			@Override
			public void draw(Canvas canvas, Paint paint) {
				canvas.drawLine(x1, y1, x2, y2, paint);
			}
		}

		public class Line extends ShapeDrawable {
			public Line(int x1, int y1, int x2, int y2, int color) {
				super(new LineShape(x1,y1,x2,y2));
				setBounds(((LineShape)this.getShape()).getBounds());
				getPaint().setStrokeWidth(3);
				getPaint().setColor(color);
			}
		}

		public class Rectangle extends ShapeDrawable {
			public Rectangle(int x, int y, int w, int h, int color) {
				super(new RectShape());
				setBounds(x, y, x + w, y + h);
				getPaint().setColor(color);
			}
		}

		public class Oval extends ShapeDrawable {
			public Oval(int x, int y, int w, int h, int color) {
				super(new OvalShape());
				setBounds(x, y, x + w, y + h);
				getPaint().setColor(color);
			}
		}

		public class Circle extends ShapeDrawable {
			public Circle(int x, int y, int r, int color) {
				super(new OvalShape());
				setBounds(x-r, y-r, x+r, y+r);
				getPaint().setColor(color);
			}
		}

		public class PathLine extends ShapeDrawable {
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

		private Map<String, ShapeDrawable> providerShapes = new HashMap<>();
		private Map<String, ShapeDrawable> consumerShapes = new HashMap<>();
		private Map<String, ShapeDrawable> duConsumerShapes = new HashMap<>();
		private Map<String, ShapeDrawable> duProviderShapes = new HashMap<>();

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

		public class Logger extends PrintWriter
		{
			private File file;

			public Logger(File file) throws FileNotFoundException {
				super(new FileOutputStream(file));
				println("log"+file.getAbsolutePath());
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

			public void print(Item item) {
				if(item==null) return;
				String type = item.getType();
				if(type!=null) {
					switch(type) {
						case "deployableUnit":
							print(String.format("<deployableUnit group=\"%s\" id=\"%s\" name=\"%s\">\n", "", "", item.getName()));
							break;
						case "consumerPorts":
							print(String.format("  <consumerPorts>\n"));
							break;
						case "consumerPort":
							print(String.format("    <consumerPort serviceClass=\"%s\" id=\"%s\" />\n", item.getProperty("class"), "" ));
							break;
						case "providerPorts":
							print(String.format("  <providerPorts>\n"));
							break;
						case "providerPort":
							print(String.format("    <providerPort serviceClass=\"%s\" id=\"%s\" />\n", item.getProperty("class"), "" ));
							break;
						default:
							break;
					}
				}

				if(item.hasChildren()) {
					for(Item child : item.getChildren()) {
						print(child);
					}
				}

				if(type!=null) {
					switch(item.getType()) {
						case "deployableUnit":
							print(String.format("</deployableUnit>\n"));
							break;
						case "consumerPorts":
							print(String.format("  </consumerPorts>\n"));
							break;
						case "consumerPort":
							break;
						case "providerPorts":
							print(String.format("  </providerPorts>\n"));
							break;
						case "providerPort":
							break;
						default:
							break;
					}
				}
			}
		}

		private int RED    = 0xffe56567;
		private int GREEN  = 0xff346b35;
		private int BLUE   = 0xff491792;
		private int YELLOW = 0xfff9ca33;
		private int ORANGE = 0xfff79433;
		private int VIOLET = 0xff6b346a;

		public String lastName(String name) {
			return name.substring(name.lastIndexOf(".")+1);
		}

		List<ShapeDrawable> moved = new ArrayList<>();
		Map<ShapeDrawable, List<ShapeDrawable>> port2labels = new HashMap<>();

		int selfConsumerIX = 0;
		int selfProviderIX = 0;

		int duConsumerIX = 40;
		int duProviderIX = 40;

		int consumerIX = 40;
		int providerIX = 40;

		int[] colors = new int[] { RED, GREEN, BLUE, ORANGE, VIOLET, YELLOW };
		int icolor = 0;

		class Component {

			private int px = 0;
			private int py = 0;
			private int pw = 0;
			private int ph = 0;

			public Rect getBounds() {
				return new Rect(px, py, px+pw, py+ph);
			}

			public int getColor(int a, int r, int g, int b) {
				int color = (a & 0xff) << 24 | (r & 0xff) << 16 | (g & 0xff) << 8 | (b & 0xff);
				return color;
			}

			public void addPathLine(ShapeDrawable a, ShapeDrawable b, int dx, int color) {
				int x1 = a.getBounds().centerX();
				int y1 = a.getBounds().centerY();
				int x2 = b.getBounds().centerX();
				int y2 = b.getBounds().centerY();

				int x = (x1 > x2 ? x1 - dx : x1 + dx);

				Path path = new Path();
				path.moveTo(x1,y1);
				path.lineTo(x,y1);
				path.lineTo(x,y2);
				path.lineTo(x2,y2);
				path.setFillType(Path.FillType.WINDING);
				view.add(new PathLine(path, color));
			}

			public void addPathLine(ShapeDrawable[] points, int color) {
				int x1 = points[0].getBounds().centerX();
				int y1 = points[0].getBounds().centerY();
				Path path = new Path();
				path.moveTo(x1, y1);
				for(int i=1; i<points.length; i++) {
					int x2 = points[i].getBounds().centerX();
					int y2 = points[i].getBounds().centerY();
					if(i%2==1) {
						path.lineTo(x2,y1);
						path.lineTo(x2,y2);
					} else {
						path.lineTo(x1,y2);
						path.lineTo(x2,y2);
					}
					x1=x2;
					y1=y2;
				}
				path.setFillType(Path.FillType.WINDING);
				view.add(new PathLine(path, color));
			}

			class ComponentPort {

				Rectangle port;
				String consumerID;
				String serviceClass;

				ComponentPort(Rectangle port, String consumerID, String serviceClass) {
					this.port = port;
					this.consumerID = consumerID;
					this.serviceClass = serviceClass;
				}

			}

			List<ComponentPort> consumerPorts = new ArrayList<>();
			List<ComponentPort> providerPorts = new ArrayList<>();

			/**
			 * L1 Components
			 */
			public Component(Item c, int px, int py) {

				this.px = px;
				this.py = py;
				this.pw = 200;
				this.ph = 200;

				String id = c.getProperty("id");
				if(id!=null) {
					view.add(new Text(px+(pw/2), py-20, pw, 20, 0xff202020, id.substring(id.lastIndexOf(".")+1), 24.0f, Paint.Align.CENTER));
				}

				int nports = 0;

				Item consumers = c.getChild("consumers");
				if(consumers!=null) {
					int x = px;
					int y = py;
					int w = 10;
					int h = 10;

					Collection<Item> children = consumers.getChildren();
					if(children.size()>nports) {
						nports = children.size();
					}

					for(Item child : children) {
						String consumerID = child.getProperty("id");
						String serviceClass = child.getProperty("serviceClass");
						Rectangle componentPort = new Rectangle(x - 10, y + 10, w, h, 0xff202020);
						view.add(componentPort);
						consumerPorts.add(new ComponentPort(componentPort, consumerID, serviceClass));
						if(consumerID!=null) {
							Text labelConsumerID = new Text(x - 20, y + 10, 400, 12, 0xff202080, aliasGet(consumerID), 12.0f, Paint.Align.RIGHT);
							view.add(labelConsumerID);
							if(!port2labels.containsKey(componentPort)) port2labels.put(componentPort, new ArrayList<ShapeDrawable>());
							port2labels.get(componentPort).add(labelConsumerID);
							if (serviceClass != null) {
								Text labelServiceClass = new Text(x-20, y+26, 400, 12, 0xff202080, aliasGet(serviceClass), 12.0f, Paint.Align.RIGHT);
								view.add(labelServiceClass);
								if(!port2labels.containsKey(componentPort)) port2labels.put(componentPort, new ArrayList<ShapeDrawable>());
								port2labels.get(componentPort).add(labelServiceClass);
							}
						}
						y += 40;
					}
				}

				Item providers = c.getChild("providers");
				if(providers!=null) {
					int x = px + pw;
					int y = py;
					int w = 10;
					int h = 10;

					Collection<Item> children = providers.getChildren();
					if(children.size()>nports) {
						nports = children.size();
					}

					for(Item child : children) {
						String providerID = child.getProperty("id");
						String serviceClass = child.getProperty("serviceClass");
						Rectangle componentPort = new Rectangle(x, y+10, w, h, 0xff202020);
						view.add(componentPort);
						providerPorts.add(new ComponentPort(componentPort, providerID, serviceClass));
						if(providerID!=null) {
							Text labelProviderID = new Text(x+20, y+12, 400, 12, 0xff202080, aliasGet(providerID), 12.0f, Paint.Align.LEFT);
							view.add(labelProviderID);
							if(!port2labels.containsKey(componentPort)) port2labels.put(componentPort, new ArrayList<ShapeDrawable>());
							port2labels.get(componentPort).add(labelProviderID);
							if(serviceClass!=null) {
								Text labelServiceClass = new Text(x+20, y+26, 400, 12, 0xff202080, aliasGet(serviceClass), 12.0f, Paint.Align.LEFT);
								view.add(labelServiceClass);
								if(!port2labels.containsKey(componentPort)) port2labels.put(componentPort, new ArrayList<ShapeDrawable>());
								port2labels.get(componentPort).add(labelServiceClass);
							}
						}
						y += 40;
					}
				}

				if(((nports*40)+10) > this.ph) {
					this.ph = (nports*40)+10 ;
				}

				view.add(new Rectangle(px, py, pw, ph, 0xff808080));
			}

			public void wiringProviders(Composition composition) {
				for(ComponentPort providerPort : providerPorts) {
					Rectangle componentPort = providerPort.port;
					String providerID = providerPort.consumerID;
					String serviceClass = providerPort.serviceClass;
					if(providerID!=null) {
						ShapeDrawable compositionPort = providerShapes.get(providerID);
						if(compositionPort!=null) {
							if(!moved.contains(compositionPort)) {
								Rect b1 = compositionPort.getBounds();
								Rect b2 = componentPort.getBounds();
								int dy = b2.top-b1.top;
								compositionPort.setBounds(new Rect(b1.left, b2.top, b1.right, b2.bottom));
								for(ShapeDrawable label : port2labels.get(compositionPort)) {
									Rect b3 = label.getBounds();
									label.setBounds(new Rect(b3.left, b3.top+dy, b3.right, b3.bottom+dy));
								}
								moved.add(compositionPort);
								composition.setProviderPort(serviceClass, compositionPort);
							}
							providerIX += 10;
							addPathLine(compositionPort, componentPort, providerIX, colors[icolor]);
							if(duProviderShapes.containsKey(serviceClass)) {
								ShapeDrawable duport = duProviderShapes.get(serviceClass);
								if(!moved.contains(duport)) {
									Rect b1 = duport.getBounds();
									Rect b2 = compositionPort.getBounds();
									int dy = b2.top-b1.top;
									duport.setBounds(new Rect(b1.left, b2.top, b1.right, b2.bottom));
									for(ShapeDrawable label : port2labels.get(duport)) {
										Rect b3 = label.getBounds();
										label.setBounds(new Rect(b3.left, b3.top+dy, b3.right, b3.bottom+dy));
									}
									moved.add(duport);
								}
								duProviderIX += 10;
								addPathLine(compositionPort, duport, duProviderIX, colors[icolor]);
							}
						}
					}
				}
			}

			public void wiringConsumers(Composition composition) {
				for(ComponentPort consumerPort : consumerPorts) {
					Rectangle componentPort = consumerPort.port;
					String consumerID = consumerPort.consumerID;
					String serviceClass = consumerPort.serviceClass;
					if(consumerID!=null) {
						ShapeDrawable compositionPort = consumerShapes.get(consumerID);
						if(compositionPort!=null) {
							if(!moved.contains(compositionPort)) {
								Rect b1 = compositionPort.getBounds();
								Rect b2 = componentPort.getBounds();
								int dy = b2.top-b1.top;
								compositionPort.setBounds(new Rect(b1.left, b1.top+dy, b1.right, b1.bottom+dy));
								for(ShapeDrawable label : port2labels.get(compositionPort)) {
									Rect b3 = label.getBounds();
									label.setBounds(new Rect(b3.left, b3.top+dy, b3.right, b3.bottom+dy));
								}
								moved.add(compositionPort);
							}
							consumerIX += 10;
							addPathLine(compositionPort, componentPort, consumerIX, colors[icolor]);
							if (duConsumerShapes.containsKey(serviceClass)) {
								ShapeDrawable duport = duConsumerShapes.get(serviceClass);
								if(!moved.contains(duport)) {
									Rect b1 = duport.getBounds();
									Rect b2 = compositionPort.getBounds();
									int dy = b2.top-b1.top;
									duport.setBounds(new Rect(b1.left, b1.top+dy, b1.right, b1.bottom+dy));
									for(ShapeDrawable label : port2labels.get(duport)) {
										Rect b3 = label.getBounds();
										label.setBounds(new Rect(b3.left, b3.top+dy, b3.right, b3.bottom+dy));
									}
									moved.add(duport);
								}
								duConsumerIX += 10;
								addPathLine(duport, compositionPort, duConsumerIX, colors[icolor]);
							} else {
								selfConsumerIX+=10;
								int dx = 200 - selfConsumerIX;
								int dy = composition.getBounds().bottom+dx;

								ShapeDrawable providerPort = composition.getProviderPort(serviceClass);

								Circle waypoint1 = new Circle(compositionPort.getBounds().centerX() - dx, compositionPort.getBounds().centerY(), 5, 0);
								Circle waypoint2 = new Circle(compositionPort.getBounds().centerX() - dx, dy, 5, 0);
								Circle waypoint3 = new Circle(compositionPort.getBounds().centerX()+1000+dx, dy, 5, 0);
								Circle waypoint4 = new Circle(compositionPort.getBounds().centerX()+1000+dx, providerPort.getBounds().centerY(), 5, 0);

								addPathLine(new ShapeDrawable[] { compositionPort, waypoint1, waypoint2, waypoint3, waypoint4, providerPort }, colors[icolor]);
							}
						}
					}
				}
			}
		}

		class Composition {

			private List<Component> components = new ArrayList<>();

			private int px = 0;
			private int py = 0;
			private int pw = 0;
			private int ph = 0;

			Map<String, ShapeDrawable>  providerPorts = new HashMap<>();

			public void setProviderPort(String serviceClass, ShapeDrawable compositionPort) {
				providerPorts.put(serviceClass, compositionPort);
			}

			public ShapeDrawable getProviderPort(String serviceClass) {
				return providerPorts.get(serviceClass);
			}

			public Rect getBounds() {
				return new Rect(px, py, px+pw, py+ph);
			}

			public Composition(Item c, int px, int py) {

				this.px = px;
				this.py = py;
				this.pw = 1000;
				this.ph = 200;

				String name = c.getParent().getProperty("name");
				if(name!=null) {
					view.add(new Text(px+(pw/2), py-20, pw, 20, 0xff202020, name, 24.0f, Paint.Align.CENTER));
				}
				int nports = 0;

				Item consumerPorts = c.getChild("consumerPorts");
				if(consumerPorts!=null) {
					int x = px;
					int y = py;
					int w = 10;
					int h = 10;

					Collection<Item> children = consumerPorts.getChildren();
					if(children.size()>nports) {
						nports = children.size();
					}

					for(Item child : children) {
						Rectangle compositionPort = new Rectangle(x-10, y+10, w, h, 0xff202020);
						view.add(compositionPort);
						String consumerID = child.getProperty("consumerId");
						if(consumerID!=null) {
							consumerShapes.put(consumerID, compositionPort);
							Text labelConsumerID = new Text(x-20, y+10, 400, 20, 0xff202080, aliasGet(consumerID), 12.0f, Paint.Align.RIGHT);
							view.add(labelConsumerID);
							if(!port2labels.containsKey(compositionPort)) port2labels.put(compositionPort, new ArrayList<ShapeDrawable>());
							port2labels.get(compositionPort).add(labelConsumerID);
						}
						y += 40;
					}
				}

				Item providerPorts = c.getChild("providerPorts");
				if(providerPorts!=null) {
					int x = px + pw;
					int y = py;
					int w = 10;
					int h = 10;

					Collection<Item> children = providerPorts.getChildren();
					if(children.size()>nports) {
						nports = children.size();
					}

					for(Item child : children) {
						Rectangle compositionPort = new Rectangle(x, y + 10, w, h, 0xff202020);
						view.add(compositionPort);
						String providerID = child.getProperty("providerId");
						if(providerID!=null) {
							providerShapes.put(providerID, compositionPort);
							Text labelProviderID = new Text(x+20, y+10, 400, 20, 0xff202080, aliasGet(providerID), 12.0f, Paint.Align.LEFT);
							view.add(labelProviderID);
							if(!port2labels.containsKey(compositionPort)) port2labels.put(compositionPort, new ArrayList<ShapeDrawable>());
							port2labels.get(compositionPort).add(labelProviderID);
						}
						y += 40;
					}
				}

				if(((nports*40)+10) > this.ph) {
					this.ph = (nports*40)+10 ;
				}

				Rectangle compositionRectangle;
				view.add(compositionRectangle = new Rectangle(px, py, pw, ph, 0xffc0c0c0));

				Item components = c.getChild("components");
				if(components!=null) {
					int x = px + 400;
					int y = py + 100;

					Collection<Item> children = components.getChildren();
					for(Item child : children) {
						Component component = new Component(child, x, y);
						Rect bounds = component.getBounds();
						y = bounds.bottom + 100;
						ph = y - py;
						this.components.add(component);
					}

					compositionRectangle.setBounds(px, py, px+pw, py+ph);
				}
			}

			public void wiring() {
				icolor=0;
				for(Component component : components) {
					component.wiringProviders(this);
					icolor++;
				}
				icolor=0;
				for(Component component : components) {
					component.wiringConsumers(this);
					icolor++;
				}

			}

		}

		class DeployableUnit {

			private int px = 0;
			private int py = 0;
			private int pw = 0;
			private int ph = 0;
			private Rectangle duRectangle;

			private Map<String, Rectangle> ports = new HashMap<>();

			public Map<String, Rectangle> getPorts() {
				return ports;
			}

			public Rect getBounds() {
				return new Rect(px, py, px+pw, py+ph);
			}

			public void setBounds(Rect rect) {
				this.px = rect.top;
				this.py = rect.left;
				this.pw = rect.width();
				this.ph = rect.height();
				duRectangle.setBounds(rect);
			}

			public DeployableUnit(Item du, int px, int py) {

				this.px = px;
				this.py = py;
				this.pw = 2000;
				this.ph = 200;

				String name = du.getProperty("name");
				if(name!=null) {
					view.add(new Text(px+(pw/2), py-20, pw, 24, 0xff202020, name, 24.0f, Paint.Align.CENTER));
				}

				int nports = 0;

				Item consumerPorts = du.getChild("consumerPorts");
				if(consumerPorts!=null) {
					int x = px;
					int y = py;
					int w = 10;
					int h = 10;

					Collection<Item> children = consumerPorts.getChildren();
					if(children.size()>nports) {
						nports = children.size();
					}

					for(Item child : children) {
						Rectangle duPort = new Rectangle(x - 10, y + 10, w, h, 0xff202020);
						view.add(duPort);
						String serviceClass = child.getProperty("serviceClass");
						if(serviceClass!=null) {
							duConsumerShapes.put(serviceClass, duPort);
							if(log!=null) {
								log.print("add="+serviceClass+"\n");
							}
							serviceClass = aliasGet(serviceClass);
							Text labelServiceClass = new Text(x-20, y+25, 500, 20, 0xff202080, serviceClass, 20.0f, Paint.Align.RIGHT);
							view.add(labelServiceClass);
							if(!port2labels.containsKey(duPort)) port2labels.put(duPort, new ArrayList<ShapeDrawable>());
							port2labels.get(duPort).add(labelServiceClass);
							ports.put(serviceClass, duPort);
						}
						y += 40;
					}
				}

				Item providerPorts = du.getChild("providerPorts");
				if(providerPorts!=null) {
					int x = px + pw;
					int y = py;
					int w = 10;
					int h = 10;

					Collection<Item> children = providerPorts.getChildren();
					if(children.size()>nports) {
						nports = children.size();
					}

					for(Item child : children) {
						Rectangle duPort = new Rectangle(x, y + 10, w, h, 0xff202020);
						view.add(duPort);
						String serviceClass = child.getProperty("serviceClass");
						if(serviceClass!=null) {
							duProviderShapes.put(serviceClass, duPort);
							serviceClass = aliasGet(serviceClass);
							Text labelServiceClass = new Text(x+20, y+25, 500, 20, 0xff202080, serviceClass, 20.0f, Paint.Align.LEFT);
							view.add(labelServiceClass);
							if(!port2labels.containsKey(duPort)) port2labels.put(duPort, new ArrayList<ShapeDrawable>());
							port2labels.get(duPort).add(labelServiceClass);
							ports.put(serviceClass, duPort);
						}
						y += 40;
					}
				}

				if(((nports*40)+10) > this.ph) {
					this.ph = (nports*40)+10 ;
				}

				view.add(duRectangle = new Rectangle(px, py, pw, ph, 0xffd8d8d8));
			}
		}

	}

	private class DownloadTask extends AsyncTask {

		@Override
		protected Object doInBackground(Object[] args) {
			if(args.length>0) {
				try {
					for(Object arg : args) {
						download((String)arg);
					}
				} catch(Exception e) {
					print(e);
				}
			} else {
				for(String key : deployableUnits.keySet()) {
					try {
						download(deployableUnits.get(key));
					} catch(Exception e) {
						print(e);
					}
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(Object o) {
			super.onPostExecute(o);
		}

		public void download(String url) throws ConnectException {
			try {

				String dir = "du";
				if(url.endsWith(".uml")) {
					dir = "uml";
				}

				String name = url.substring(url.lastIndexOf("/")+1);
				if(name.startsWith("model!uml2!")) {
					name = name.replaceFirst("model!uml2!", "");
				}

				File file = new File(new File(rootdir, dir), name);

				file.getParentFile().mkdirs();
				PrintWriter out = new PrintWriter(new FileOutputStream(file));
				BufferedReader reader = new BufferedReader(new InputStreamReader(((HttpURLConnection)(new URL(url).openConnection())).getInputStream(), Charset.forName("UTF-8")));
				String line;
				while ((line = reader.readLine()) != null) {
					out.println(line);
				}
				reader.close();
				out.close();
			} catch(Exception e) {
				if(e.getMessage().startsWith("Server returned HTTP response code: 401")) {
					print(new Error("XML download: UNAUTHORIZED (%s)!",url));
				} else  if(e.getMessage().startsWith("Server returned HTTP response code: 400")) {
					print(new Error("XML download: BAD REQUEST (%s)!",url));
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
			try {
				List<Item> items = new ArrayList<>();
				for(String arg : args) {
					Document doc = getDocument(new File(dudir, arg).getAbsolutePath());
					items.add(parseDocument(doc));
				}
				return items;
			} catch(Exception e) {
				print(e);
			}
			return null;
		}

		@Override
		protected void onPostExecute(List<Item> items) {
			super.onPostExecute(items);
			try {
				if(items!=null) {
					for(Item item : items) {
						item.setParent(root);
					}
				}
			} catch(Exception e) {
				print(e);
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

		// nodes
		private static final String TEXT            = "#text";
		private static final String DEPLOYABLE_UNIT = "deployableUnit";
		private static final String CONSUMER_PORTS  = "consumerPorts";
		private static final String CONSUMER_PORT   = "consumerPort";
		private static final String PROVIDER_PORTS  = "providerPorts";
		private static final String PROVIDER_PORT   = "providerPort";
		private static final String COMPOSITION     = "composition";
		private static final String COMPONENTS      = "components";
		private static final String COMPONENT       = "component";
		private static final String CONSUMERS       = "consumers";
		private static final String CONSUMER        = "consumer";
		private static final String PROVIDERS       = "providers";
		private static final String PROVIDER        = "provider";
		private static final String WIRING          = "wirings";

		// attributex
		private static final String GROUP              = "group";
		private static final String ID                 = "id";
		private static final String NAME               = "name";
		private static final String SERVICE_CLASS      = "serviceClass";
		private static final String CONSUMER_ID        = "consumerId";
		private static final String PROVIDER_ID        = "providerId";
		private static final String ACTIVATOR_CLASS    = "activatorClass";
		private static final String COMPONENT_PROPERTY = "componentProperty";
		private static final String MULTIPLICITY       = "multiplicity";
		private static final String SCOPE              = "scope";

		public Item parseDocument(Document doc) {
			Item du = null;
			Element root = doc.getDocumentElement();
			String nodeName = root.getNodeName();

			if(verbose) {
				print("<b>%s</b><br>",nodeName);
				String text = root.getTextContent();
				if(text!=null && !text.isEmpty()) {
					print("%s<br>",text);
				}
			}

			switch(nodeName) {
				case DEPLOYABLE_UNIT:
					du = new Item(null, nodeName, root.getAttribute(ID), null, null);
					break;
				default:
					print(new Error("unexpected root nodeName=\"%s\"",nodeName));
					return null;
			}

			if (root.hasAttributes()) { 
				NamedNodeMap attributes = root.getAttributes();
				for (int i=0; i < attributes.getLength(); i++) { 
					Attr attr = (Attr)attributes.item(i); 
					if (attr != null) { 
						String key = attr.getName(); 
						String val = attr.getValue(); 
						print("attribute: %s=%s<br>", key, val); 
						if(du!=null) {
							du.setProperty(key, val);
						}
					}
				}
			}

			if(root.hasChildNodes()) {
				print("<ul>");
				NodeList children = root.getChildNodes();
				for(int i=0; i< children.getLength(); i++) { 
					Node child = children.item(i);
					parseNode(child, du);
				}
				print("</ul>");
			}

			return du;
		}

		public void parseNode(Node node, Item parent) {
			String nodeName = node.getNodeName();

			Item item = null;
			switch(nodeName) {
				case TEXT:
					return;
				case CONSUMER_PORTS:
				case PROVIDER_PORTS:
				case COMPONENTS:
				case CONSUMERS:
				case PROVIDERS:
				case COMPOSITION:
					item = new Item(parent, null, nodeName, null, null);
					break;
				case CONSUMER_PORT:
				case PROVIDER_PORT:
				case COMPONENT:
				case CONSUMER:
				case PROVIDER:
				case WIRING:
					item = new Item(parent, nodeName, ((Element)node).getAttribute(ID), null, null);
					break;
				default:
					print(new Error("unexpected node nodeName=\"%s\"", nodeName));
					return;
			}
			print("<li>");
			if(verbose) {
				print("<b>%s</b><br>",nodeName);
				String text = node.getTextContent();
				if(text!=null && !text.trim().isEmpty()) {
					print("TEXT:%s<br>",text);
				}
			}

			if (node.hasAttributes()) { 
				NamedNodeMap attributes = node.getAttributes();
				for (int i=0; i < attributes.getLength(); i++) { 
					Attr attr = (Attr)attributes.item(i); 
					if (attr != null) { 
						String key = attr.getName(); 
						String val = attr.getValue(); 
						print("attribute: %s=%s<br>", key, val); 
						if(item!=null) {
							item.setProperty(key, val);
						}
						switch(key) {
							case GROUP:
								break;
							case ID:
								break;
							case NAME:
								break;
							case SERVICE_CLASS:
								break;
							case CONSUMER_ID:
								break;
							case PROVIDER_ID:
								break;
							case ACTIVATOR_CLASS:
								break;
							case COMPONENT_PROPERTY:
								break;
							case MULTIPLICITY:
								break;
							case SCOPE:
								break;
							default:
								print(new Error("unexpected attribute name \"%s\" for node name \"%s\"", key, nodeName));
								break;
						}
					}
				}
			}

			if(node.hasChildNodes()) {
				print("<ul>");
				NodeList children = node.getChildNodes();
				for(int i=0; i< children.getLength(); i++) { 
					Node child = children.item(i);
					parseNode(child, item);
				}
				print("</ul>");
			}
			print("</li>");
		}
	}

	public static class Item implements Serializable {

		public static final long serialversionUID = 1L;

		public static String getUniqueName(Item parent, String name) {
			if(parent!=null && parent.getChild(name)!=null) {
				int i = 0;
				while(parent.getChild(name+"_"+i)!=null) {
					i++;
				}
				name = name+"_"+i;
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
			child.parent = this;
			if(child.getName()!=null) {
				index.put(child.getName(), child);
			}
		}

		private void removeChild(Item child) {
			children.remove(child);
			child.parent = null;
			if(child.getName()!=null) {
				index.remove(child.getName());
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
			if(this.parent!=null) {
				parent.removeChild(this);
			}
			if(parent!=null) {
				parent.addChild(this);
			} else {
				this.parent = parent;
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

	public void println(String fmt, Object... args) {
		print(fmt+"<br>",args);
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

	public void authenticate(final String user, final String pswd) {
		Authenticator.setDefault(new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(user, pswd.toCharArray());
				}
			});
	}

}
