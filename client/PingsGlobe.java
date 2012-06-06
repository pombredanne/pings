import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;

import com.jhlabs.map.MapMath;
import com.jhlabs.map.ProjectionPainter;

/**
 * The globe component modified to show pings
 * <p>
 * This JComponent draw a globe and some visual effect for stored pings (up to
 * stored_pings_size =  { @value stored_ping_size} pings can be stored and 
 * displayed.
 */
public class PingsGlobe extends Globe {
	
	private static final long serialVersionUID = 1L;
	
	private GeoipInfo origin;
	private PingGUI[] stored_pings;
	private static final int stored_pings_size = 20;
	private static final float attenuation_offset_for_cities = 2f / 20f;
	
	//The last index of the array used to store a ping, as null case are handle
	//its value as no impact as long as it's in the bounds of the array
	private int last_ping = 0 ;
	
	private static final Color origin_color = new Color(44f / 255f, 63f / 255f, 201f / 255f,	0.9f);
	private static final float[] waiting_color = {195,208,226};//{ 69, 178, 110};	
	private static final float[] timed_out_color = {131, 13, 44};	
	private static final float[] connection_refused_color = {20, 20, 20};
	private static final float[] unknown_error_color = {255, 255, 255};
	private float prefered_font_size = 13.5f;
	private BasicStroke link_stroke = new BasicStroke(2.5f);

	private Graphics2D text_render;

	
	
	private static final float [][] color_scale = {
		{0,255,0,			0.025f },
		{73,255,67,			0.050f },
		{106,255,97,		0.075f },
		{134,243,97,		0.100f },
		{246,242,35,		0.150f },
		{246,215,35,		0.200f },
		{222,161,62,		0.250f },
		{192,111,60,		0.300f },
		{168,21,42,			0.400f },
		{137,18,28,			0.500f },
		{92,32,40,			0.600f },
		{78,57,58,			0.700f },
		{76,76,76,			0.850f },
		{31,29,29,			1.000f },
	};
	
	
	public PingsGlobe() {
		super();
		
		//The globe currently shows only the landmasses
		super.setShowGraticule(false);
		super.setShowTissot(false);
		super.setShowSea(true);
		// The night could be added using
		super.setShowDay(true);
		// although this method currently add a fixed black circle which wouldn't
		// show much.
		
		stored_pings = new PingGUI[stored_pings_size];
	}
	
	class PingGUI {
		
		private GeoipInfo target;
		private double value = -1;
		float[] color = waiting_color;
		
		public PingGUI(GeoipInfo target) {
			this.target = target;
		}
		
		private void UpdateColor() {
			if (value == -1) {color = waiting_color;}
			else if (value == -2) {color = timed_out_color;}
			else if (value == -3) {color = unknown_error_color;}
			else if (value == -4) {color = connection_refused_color;}
			else {
				int i = 0;
				while ((color_scale[i][3] < value) && (i < color_scale.length-1))
					{i++;}
				color = color_scale[i];
			}
		}
		
		public GeoipInfo getGeoip() {
			return target;
		}
		
		public void setValue(double new_ping_value) {
			this.value = new_ping_value;
			UpdateColor();
			PingsGlobe.this.repaint();
		}
		
		public void connectionRefused () {
			this.value = -4;
			UpdateColor();
			PingsGlobe.this.repaint();
		}
		
		public void unknownError() {
			this.value = -3;
			UpdateColor();
			PingsGlobe.this.repaint();
		}
		
		public void timedOut() {
			this.value = -2;
			UpdateColor();
			PingsGlobe.this.repaint();
		}
		
		public void noResultsYet () {
			this.value = -1;
			UpdateColor();
			PingsGlobe.this.repaint();
		}
		
		private void add_target_circle(GeneralPath gc, float circle_radius) {
			ProjectionPainter.smallCircle(
					(float) target.longitude,(float) target.latitude,
					circle_radius, 15, gc, true);
			gc.closePath();
		}
		
		private void add_arc(GeneralPath gc) {
			//FIXME: changed just to see the arcs even with 'failed' pings
			if (origin== null /* || value < 0*/) return;
	
			g2.setStroke(link_stroke);
			ProjectionPainter.basicArc(
					(float) origin.longitude,(float) origin.latitude,
					(float) target.longitude,(float) target.latitude,
					gc);
		}
		
		private void paint_description_text(Color peer_color, float circle_radius) {
			Point2D.Double target_geo = new Point2D.Double(target.longitude, target.latitude);
			
			projection.transform(target_geo,target_geo);
			
			String description;
			if (target.city != null && !target.city.equals("")) {
				description = target.city + ", " + target.country;
			}
			else if (target.country != null) {
				description = target.country;
			}
			else {
				description = "Unknow";
			}
			
			if (value > 0) {
				if (value < 10) {
					long ms_value = Math.round(1000 * value);
					description+= " : " + ms_value + " ms";
				}
				else {
					long s_value = Math.round(value);
					description+= " : " + s_value + " s";
				}
			}
			else if (value > 0 ) {
				description+= " : " + ((int) (value)) + " s";
			}
			else if (value == -1) {
				description+= " : Pinging";
			}
			else if (value < 0) {
				description+= " : Error";
			}
			
			text_render.setColor(peer_color);
			text_render.drawString( description , (int) target_geo.x + circle_radius,(int)- target_geo.y - (3 * circle_radius) );
		}
		
		
		
		/**
		 * Draw a circle at the end point and an arc joining it with the origin.
		 */
		private void paint(Graphics2D g2, float color_attenuation, float circle_radius) {
			if (target == null) return;
			
			//Set the parameter for this drawing
			Color peer_color = new Color(
					color[0]/255f,
					color[1]/255f,
					color[2]/255f,
					color_attenuation);
			
			//Draw the circle around the target
			GeneralPath gc = new GeneralPath();
			ProjectionPainter pp = ProjectionPainter.getProjectionPainter(projection);
			
			add_target_circle(gc, circle_radius);
			pp.drawPath(g2, gc, null,peer_color);
			
			gc = new GeneralPath();
			add_arc(gc);
			pp.drawPath(g2, gc, peer_color,null);
			
			
			//Draw the description above the target
			if ((color_attenuation > attenuation_offset_for_cities) &&
					(isVisible(target.longitude,target.latitude))) {
				paint_description_text(peer_color, circle_radius);
			}
			
		}

		public void updatePingGUIValue(String value) {
			//final String regex = "\\S+\\s\\S+\\s(\\d+)\\s(\\d+)\\s(\\d+).+";
			
			try {

				String[] groups = value.split(" |ms",6);
				int nb_try = Integer.parseInt(groups[2]);
				int nb_worked = Integer.parseInt(groups[3]);
				float totaltime = Float.parseFloat(groups[4]) /1000f;
				if (nb_worked == 0) {
					this.connectionRefused();
				}
				else
				if (nb_worked < nb_try -1 )
				{
					this.unknownError();
				}
				else 
				{
					this.setValue(totaltime /nb_try );
				}
			}
			catch (Exception e) {
				this.unknownError();
			}
		}
		
	}
	
	/**
	 * Select the origin on the globe : this will add a circle around it and 
	 * tell where to draw the arcs from. Additionally it centers the view on it.
	 * 
	 * @see #paintOrigin(Graphics2D, GeoipInfo)
	 * @see #paintLink(Graphics2D, GeoipInfo, GeoipInfo)
	 * 
	 * @param origin the client geoip to set origin on
	 */
	public void setOrigin(GeoipInfo origin) {
		this.origin = origin;
		centerView(origin);
	}
	
	/**
	 * Add a ping to be drawn on the globe. For the viewer convenience only 
	 * stored_ping_size = { @value stored_ping_size} pings can be stored and 
	 * displayed.
	 * 
	 * @param pinginfo the information on the new ping to add
	 */
	public PingGUI addPing(GeoipInfo pinginfo) {
		PingGUI newping = new PingGUI (pinginfo);
		last_ping = (last_ping + 1 ) % stored_pings_size;
		stored_pings[last_ping] = newping;
		this.repaint();
		return newping;
	}

	/**
	 * Center the projection to have a good view on the given point.
	 * <p>
	 * This function doesn't actually center the view exactly on the point 
	 * itself but on a point 0.7 rad W to it.
	 * 
	 * @param new_center the point to look at
	 */
	public void centerView(GeoipInfo new_center) {
		projection.setProjectionLatitude(MapMath.DTR * new_center.latitude);
		projection.setProjectionLongitude(MapMath.DTR * new_center.longitude - 0.7);
		projection.initialize();
		this.repaint();
	}
	
	/**
	 * Paint the origin (that is the client geoip) as a circle on the globe
	 * using the Graphics g2.
	 * @param g2 the Graphics2D to draw on
	 * @param origin the GeoipInfo of the origin
	 */
	private void paintOrigin(Graphics2D g2, GeoipInfo origin, float circle_radius) {
		if (origin == null ) return;
		GeneralPath gc = new GeneralPath();
		ProjectionPainter.smallCircle(
				(float)origin.longitude, (float) origin.latitude,
				circle_radius,20, gc, true);
		gc.closePath();
		ProjectionPainter pp = ProjectionPainter.getProjectionPainter(projection);
		pp.drawPath(g2, gc, null, origin_color);
	}
	
	/**
	 * The methods that draw the globe.
	 * <p>
	 * @see Globe
	 * @see #paintOrigin(Graphics2D, GeoipInfo)
	 * @see #paintLink(Graphics2D, GeoipInfo, GeoipInfo)
	 */
	public void paint(Graphics g) {
		
		//Paint the globe
		super.paint(g);
		
		//set_fast_graphics(g2);
		
		//Paint the targets
		
		//Set up the text rendering
		//Create a new graphics for the text to be able to select a different 
		//transformation and a different font
		
		//The new transformation is used to draw the text upward
		text_render = (Graphics2D) g2.create();
		AffineTransform uptransform = new AffineTransform();
		uptransform.translate(getWidth()/2,getHeight()/2);
		uptransform.concatenate(transform);
		text_render.setTransform(uptransform);
		
		//Calculate a new font according to the current zoom
		int screenRes = Toolkit.getDefaultToolkit().getScreenResolution();
	    int fontSize = (int)Math.round(prefered_font_size  * screenRes / 72.0 / uptransform.getScaleX());
		Font font = new Font("Arial", Font.PLAIN, fontSize);
		text_render.setFont(font);
		
		//Calculate a new circle radius according to the current zoom
		float circle_radius = (float) (2 / uptransform.getScaleX());
		link_stroke = new BasicStroke(2 * circle_radius );
		
		//We paint the currently stored pings
		int actual_index = last_ping;
		for (int i = 0;i < stored_pings_size ;i++) {
			PingGUI current_ping = stored_pings[actual_index];
			if (current_ping != null) {
				float color_attenuation =
					((float) (stored_pings_size - i))
					/
					((float) stored_pings_size);
				current_ping.paint(g2,color_attenuation,circle_radius);
			}
			actual_index--;
			if (actual_index == -1) actual_index = stored_pings_size -1;
		}
		
		//Paint the origin (the client position)
		paintOrigin(g2, origin, 1.75f * circle_radius);
	
	}
}