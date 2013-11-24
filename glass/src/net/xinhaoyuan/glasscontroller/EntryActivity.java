package net.xinhaoyuan.glasscontroller;

import java.net.URLDecoder;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.xinhaoyuan.glasscontroller.BluetoothPacketService.ConnectionState;

import com.google.android.glass.app.Card;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class EntryActivity extends Activity implements GestureDetector.BaseListener {
	public static final String BT_SERVICE_NAME = "GlassController";
	public static final int CANCEL_DELAY = 500;
	public static final int ADJUST_DELAY = 1200;
	
	private GestureDetector _detector;
	
	class ControllerBTService extends BluetoothPacketService {
		public ControllerBTService() {
			super(BT_SERVICE_NAME, PacketType.PT_LINE, new Handler(Looper.getMainLooper()));
		}
		
		public boolean getConnection() {
			return super.getConnection();
		}
		
		public boolean reset() {
			return super.reset();
		}
		
		protected void onConnectionState(boolean connected) {
			Log.d("ConnectionState", connected ? "connected" : "disconnected");
			if (connected) {
				_current_card.setText("Connected");
				_current_card.setInfo("");
				updateCard();
			} else {
				_current_card.clearImages();
				_current_card.setText("Disconnected");
				_current_card.setInfo("");
				updateCard();
			}
		}
		
		public ConnectionState getConnectionState() { return super.getConnectionState(); }
		
		public void send(String line) {
			super.sendLine(line);
		}
		
		public void onLine(String line) {
			StringTokenizer tokens = new StringTokenizer(line);
			if (tokens.countTokens() < 2) return;
			String cookie = tokens.nextToken();
			if (!cookie.equals(_cookie)) return;
			String text = tokens.nextToken();
			String info;
			try {
				info = tokens.nextToken();
			} catch (NoSuchElementException x) {
				info = "";
			}
			
			try {
				_current_card.setText(URLDecoder.decode(text, "UTF-8"));
				_current_card.setInfo(URLDecoder.decode(info, "UTF-8"));
			} catch (Exception x) { }
			updateCard();
		}
	}
	
	ControllerBTService      _service;
	private Action           _root_action;
	private CancelableAction _cancelable_action;
	private AdjustableAction _adjustable_action;
	private Action           _action;
	
	private RelativeLayout   _container, _card_container;
	private RelativeLayout.LayoutParams _card_param;
	private View _current_card_view;
	private Card _current_card;
	private TextView _pending_status;
	private String _cookie;
	private Random _random;
	
	private void updateCookie() {
		_cookie = Long.toString(_random.nextLong());
	}
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		_service = new ControllerBTService();
		_detector = new GestureDetector(this).setBaseListener(this);
		
		_random = new Random();
		updateCookie();
		
		_action = null;
		_root_action = new RootAction();
		_cancelable_action = new CancelableAction();
		_adjustable_action = new AdjustableAction();
		
		_current_card = new Card(this);
		_pending_status = new TextView(this);
		_current_card.setText("No connection");
		_current_card.setInfo("");
		
		_container = new RelativeLayout(this);
		_card_container = new RelativeLayout(this);
		
		_card_param = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); 
		
		_card_param.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		_card_param.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		_card_param.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		_card_param.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		
		_card_container.addView(_current_card_view = _current_card.toView(), _card_param);
		_container.addView(_card_container, _card_param);
		
		RelativeLayout.LayoutParams status_param = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		status_param.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		status_param.addRule(RelativeLayout.CENTER_HORIZONTAL);
		
		_container.addView(_pending_status, status_param);
		setContentView(_container);
	}
	
	private void updateCard() {
		_card_container.removeView(_current_card_view);
		_current_card_view = _current_card.toView();
		_card_container.addView(_current_card_view, _card_param);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		menu.clear();
        switch (_service.getConnectionState()) {
        case CS_UNINITIALIZED:
        	inflater.inflate(R.menu.no_connection_menu, menu);
        	return true;
        case CS_CONNECTED:
        	inflater.inflate(R.menu.connected_menu, menu);
        	return true;
        default:
        	return false;
        }
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection.
        switch (item.getItemId()) {
            case R.id.menu_connect:
            	_cancelable_action.start("Connect", new Runnable() {
            		@Override
            		public void run() {
            			_current_card.setText("Connecting");
            			_current_card.setInfo("");
        				updateCard();
                    	_service.getConnection();
            		} }, CANCEL_DELAY);
                return true;
            case R.id.menu_reset:
            	_cancelable_action.start("Disconnect", new Runnable() {
            		@Override
            		public void run() {
            			_service.reset();
            		}
            	}, CANCEL_DELAY);
            	return true;
            case R.id.menu_refresh:
            	_cancelable_action.start("Refresh", new Runnable() {
            		@Override
            		public void run() {
            			_current_card.setText("Refreshing");
            			_current_card.setInfo("");
            			updateCard();
            			updateCookie();
            			_service.send(_cookie + " refresh");
            		}
            	}, CANCEL_DELAY);
            	return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
	
	@Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return _detector.onMotionEvent(event);
    }

	@Override
	public boolean onGesture(Gesture g) {
		if (_action != null) {
			if (_action.take_gesture(g))
				return true;
			if (!_action.cancel()) return false;
		}
		
		if (_root_action.take_gesture(g))
			return true;
		
		if (g == Gesture.LONG_PRESS || g == Gesture.TAP) {
			openOptionsMenu();
			return true;
		}
		
		return false;
	}
	
	interface Action {
		public boolean can_take_gesture(Gesture g);
		public boolean can_cancel();
		public boolean take_gesture(Gesture g);
		public boolean cancel();
		public String  getName();
	}
	
	interface PRunnable {
		public void run(Object arg);
	}
	
	class DefaultAction implements Action {
		@Override
		public boolean can_take_gesture(Gesture g) {
			return take_gesture(g, false);
		}
		@Override
		public boolean can_cancel() {
			return cancel(false);
		}
		@Override
		public boolean take_gesture(Gesture g) {
			return take_gesture(g, true);
		}
		@Override
		public boolean cancel() {
			return cancel(true);
		}
		protected boolean take_gesture(Gesture g, boolean doit) {
			return false;
		}
		protected boolean cancel(boolean doit) {
			return false;
		}
		@Override
		public String getName() {
			return getClass().getName();
		}
	}
	
	class RootAction extends DefaultAction {
		String[] _action_types = { "oneshot", "oneshot", "none", "adjustable -20 20 0 5" };
		String[] _action_names = { "Prev", "Next", "", "Skip" };
		String[] _action_ids = { "prev", "next", "", "skip" };
		
		Pattern _adjustable_ptn;
		
		public RootAction() {
//			_action_types = new String[4];
//			_action_names = new String[4];
//			_action_ids = new String[4];
			
			_adjustable_ptn = Pattern.compile("^adjustable (-?[0-9]+) (-?[0-9]+) (-?[0-9]+) ([0-9]+)$", Pattern.CASE_INSENSITIVE);
		}
		
		@Override
		protected boolean take_gesture(Gesture g, boolean doit) {
			if (_service.getConnectionState() != ConnectionState.CS_CONNECTED) return false;
			int op_id = -1;
			if (g == Gesture.SWIPE_LEFT) {
				op_id = 0;
			} else if (g == Gesture.SWIPE_RIGHT) {
				op_id = 1;
			} else if (g == Gesture.TAP) {
				op_id = 2;
			} else if (g == Gesture.TWO_TAP) {
				op_id = 3;
			}
			if (op_id == -1) return false;
			if (_action_types[op_id].equalsIgnoreCase("none")) return false;
			if (doit) {
				final int id = op_id;
				Matcher m;
				if (_action_types[id].equalsIgnoreCase("oneshot")) {
					_cancelable_action.start(_action_names[id], new Runnable() {
						@Override
						public void run() {
							_current_card.setText(_action_names[id]);
							_current_card.setInfo("");
							updateCard();
							updateCookie();
							_service.send(_cookie + " " + _action_ids[id]);
						}
					}, CANCEL_DELAY);
				} else if ((m = _adjustable_ptn.matcher(_action_types[id])).matches()) {
					_adjustable_action.start(
							_action_names[id], 
							Integer.parseInt(m.group(1)),
							Integer.parseInt(m.group(2)),
							Integer.parseInt(m.group(3)),
							Integer.parseInt(m.group(4)),
							new PRunnable() {
								@Override
								public void run(Object arg) {
									int a = (Integer)arg;
									_current_card.setText(_action_names[id] + " " + a);
									_current_card.setInfo("");
									updateCard();
									updateCookie();
									_service.send(_cookie + " " + _action_ids[id] + " " + a);
								}
							}, ADJUST_DELAY);
				}
			}
			return true;
		}
	}
	
	static class CancelableRunnable implements Runnable {
		private Runnable _r;
		private boolean _canceled;
		
		public CancelableRunnable(Runnable r) {
			_r = r;
			_canceled = false;
		}
		
		public void cancel() {
			_canceled = true;
		}
		
		@Override
		public void run() {
			if (!_canceled) _r.run();
		}
	}
	
	class CancelableAction extends DefaultAction {
		CancelableRunnable _r;
		Handler _handler;
		String _name;
		
		public CancelableAction() {
			this(new Handler());
		}
		
		public CancelableAction(Handler handler) {
			_r = null;
			_handler = handler;
		}
		
		public void start(String name, final Runnable r, int delay_ms) {
			_name = name;
			_r = new CancelableRunnable(new Runnable() {
				@Override
				public void run() {
					_pending_status.setText("");
					_r = null;
					_action = null;
					r.run();
				}
			});
			_action = this;
			_handler.postDelayed(_r, delay_ms);
			_pending_status.setText(name);
		}
		
		@Override
		protected boolean cancel(boolean doit) {
			if (_r == null) return false;
			if (doit) {
				_pending_status.setText("");
				_r.cancel();
				_r = null;
				_action = null;
			}
			return true;
		}
		
		@Override
		public String getName() { if (_r != null) return _name; else return "EmptyAction"; }
	}
	
	class AdjustableAction extends DefaultAction {
		Runnable _real_action;
		CancelableRunnable _r;
		boolean _tapped;
		Handler _handler;
		String _name;
		int _min, _max, _cur, _fast_step;
		int _delay_ms;
		
		public AdjustableAction() {
			this(new Handler());
		}
		
		public AdjustableAction(Handler handler) {
			_r = null;
			_handler = handler;
		}
		
		public void start(String name, int min, int max, int cur, int fast_step, final PRunnable pr, int delay_ms) {
			_tapped = false;
			
			_name = name;

			_min = min;
			_max = max;
			if (cur > max) cur = max;
			if (cur < min) cur = min;
			_cur = cur;
			_fast_step = fast_step;
			
			_real_action = new Runnable() {
				@Override
				public void run() {
					_pending_status.setText("");
					_r = null;
					_action = null;
					pr.run(Integer.valueOf(_cur));
				}
			};
			
			_r = new CancelableRunnable(_real_action);
			
			_delay_ms = delay_ms;
			
			_action = this;
			
			_handler.postDelayed(_r, _delay_ms);
			_pending_status.setText(_name + " " + _cur);
		}
		
		public String getName() {
			if (_r == null) return "EmptyAction";
			return _name + " " + _cur;
		}
		
		@Override
		protected boolean take_gesture(Gesture g, boolean doit) {
			boolean c;
			int delta = 0;
			
			if (g == Gesture.SWIPE_LEFT) {
				c = true;
				delta = -1;
			} else if (g == Gesture.SWIPE_RIGHT) {
				c = true;
				delta = 1;
			} else if (g == Gesture.TWO_SWIPE_LEFT) {
				c = true;
				delta = -_fast_step;
			} else if (g == Gesture.TWO_SWIPE_RIGHT) {
				c = true;
				delta = _fast_step;
			} else if (g == Gesture.TWO_TAP) {
				c = true;
			} else if (g == Gesture.TAP) {
				if (doit) {
					if (_tapped) {
						_r.cancel();
						_pending_status.setText("");
						_r = null;
						_action = null;
					} else _tapped = true;
				}
				return true;
			} else c = false;
			
			if (c && doit) {
				_tapped = false;
				_cur += delta;
				if (_cur > _max) _cur = _max;
				if (_cur < _min) _cur = _min;
				_r.cancel();
				_r = new CancelableRunnable(_real_action);					
				_handler.postDelayed(_r, _delay_ms);
				_pending_status.setText(_name + " " + _cur);
			}
			
			return c;
		}
	}	
}
