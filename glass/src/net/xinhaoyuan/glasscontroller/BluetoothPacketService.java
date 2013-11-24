package net.xinhaoyuan.glasscontroller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class BluetoothPacketService {
	
	enum ConnectionState {
		CS_UNINITIALIZED,
		CS_ONGOING,
		CS_CONNECTED,
	};
	
	enum PacketType {
		PT_LINE,
		PT_SIZE_HEADED,
	};
	
	private PacketType      _pt;
	private String          _service_name;
	private Handler         _handler;
	private ConnectionState _socket_state;
	private BluetoothSocket _socket;
	private RecieveThread   _reciever;
	private Sender          _sender;
	private LooperThread    _looper;
	
	static class LooperThread extends Thread {
		private Handler _handler;
		
		private LooperThread() {
			super();
			_handler = null;
		}
		
		static public LooperThread create() {
			LooperThread t = new LooperThread();
			t.start();
			synchronized (t) {
				while (t._handler == null) {
					try { t.wait(); } catch (InterruptedException e) { }
				}
			}
			return t;
		}
		
		public void post(final Runnable r, final boolean last) {
			_handler.post(new Runnable() {
				@Override
				public void run() {
					if (r != null) r.run();
					if (last) _handler.getLooper().quit();
				}
			});
		}
		
		@Override
		public void run() {
			Looper.prepare();
			_handler = new Handler();
			synchronized (this) {
				notify();
			}
			Looper.loop();
		}
	}
	
	private class RecieveThread extends Thread {
		private boolean _stop_flag;
		private InputStream _is;
		private BufferedReader _rdr;
		
		public RecieveThread(InputStream is) {
			_stop_flag = false;
			_is = is;
			if (_pt == PacketType.PT_LINE)
				_rdr = new BufferedReader(new InputStreamReader(is));
			else _rdr = null;
		}
		
		public void cancel() {
			_stop_flag = true;
			try {
				_is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			this.interrupt();
		}
		
		@Override
		public void run() {
			while (!_stop_flag) {
				if (_pt == PacketType.PT_SIZE_HEADED) {
					final byte[] msg;
					int pkt_size = 0;
					try {
						int i;
						i = _is.read(); if (i < 0) break; else pkt_size = (pkt_size << 8) + i;
						i = _is.read(); if (i < 0) break; else pkt_size = (pkt_size << 8) + i;
						i = _is.read(); if (i < 0) break; else pkt_size = (pkt_size << 8) + i;
						i = _is.read(); if (i < 0) break; else pkt_size = (pkt_size << 8) + i;
					} catch (IOException io) {
						io.printStackTrace();
						break;
					}
					if (_stop_flag) break;
					msg = new byte[pkt_size];
					int offset = 0;
					while (!_stop_flag && offset < pkt_size) {
						long r;
						try {
							r = _is.read(msg, offset, pkt_size - offset);
						} catch (IOException e) {
							e.printStackTrace();
							break;
						}
						if (r < 0) break;
						offset += r;
					}
					_handler.post(new Runnable() {
						@Override
						public void run() {
							if (!_stop_flag)
								onPacket(msg);
						}
					});
				} else if (_pt == PacketType.PT_LINE) {
					final String line;
					try { line = _rdr.readLine(); } catch (Exception x) { break; }
					_handler.post(new Runnable() {
						@Override
						public void run() {
							if (!_stop_flag)
								onLine(line);
						}
					});
				} else {
					break;
				}
			}
			
			_handler.post(new Runnable() {
				@Override
				public void run() {
					onRecieverEnd();
				}
			});
		}
	}
	
	class Sender {
		boolean _stop_flag;
		LooperThread _t;
		OutputStream _os;
		
		public Sender(OutputStream os, LooperThread t) {
			_stop_flag = false;
			_os = os;
			_t = t;
		}
		
		public void cancel() {
			_stop_flag = true;
			try {
				_os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			_t.interrupt();
			_t.post(new Runnable() {
				@Override
				public void run() {
					_handler.post(new Runnable () {
						@Override
						public void run() {
							onSenderEnd();
						}
					});
				}
			}, false);
		}
		
		public void send(final byte[] msg, final boolean withSize) {
			_t.post(new Runnable() {
				@Override
				public void run() {
					if (_stop_flag) return;
					try {
						if (withSize) {
							int length = msg.length;
							_os.write((length & 0xFF000000) >> 24);
							_os.write((length & 0x00FF0000) >> 16);
							_os.write((length & 0x0000FF00) >> 8);
							_os.write(length & 0x000000FF);
						}
						_os.write(msg);
					} catch (Exception x) {
						x.printStackTrace();
					}
				}
			}, false);
		}
	}
	
	public BluetoothPacketService(String service_name, PacketType pt, Handler handler) {
		_service_name = service_name;
		_pt = pt;
		_handler = handler;
		_socket_state = ConnectionState.CS_UNINITIALIZED;
		_socket = null;
		_reciever = null;
		_sender = null;
		_looper = LooperThread.create();
	}
	
	protected boolean getConnection() {
		if (Looper.myLooper() != _handler.getLooper()) return false;
		if (_socket_state != ConnectionState.CS_UNINITIALIZED) return false;
		_socket_state = ConnectionState.CS_ONGOING;
		
		_looper.post(new Runnable() {
			@Override
			public void run() {
				BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
				try {
					BluetoothServerSocket server = 
							adapter.listenUsingInsecureRfcommWithServiceRecord(
									_service_name,
									UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
					BluetoothSocket sock;
					Log.d("", "Accepting");
					try {
						sock = server.accept(30000);
					} catch (IOException x) {
						x.printStackTrace();
						sock = null;
					}
					Log.d("", "Finished");
					server.close();
					
					final BluetoothSocket _s = sock;
					_handler.post(new Runnable() {
						@Override
						public void run() {
							InputStream is;
							OutputStream os;
						
							try {
								is = _s.getInputStream();
								os = _s.getOutputStream();
							} catch (Exception x) {
								_socket_state = ConnectionState.CS_UNINITIALIZED;
								onConnectionState(false);
								return;
							}
							
							_reciever = new RecieveThread(is);
							_reciever.start();
							_sender = new Sender(os, _looper);
							_socket = _s;
							_socket_state = ConnectionState.CS_CONNECTED;
							onConnectionState(true);
						}
					});
				} catch (IOException e) {
					e.printStackTrace();
					_handler.post(new Runnable() {
						@Override
						public void run() {
							_socket_state = ConnectionState.CS_UNINITIALIZED;
							onConnectionState(false);
						}
					});
				}
			}
		}, false);
		
		return true;
	}
	
	protected boolean reset() {
		if (Looper.myLooper() != _handler.getLooper()) return false;
		if (_socket_state != ConnectionState.CS_CONNECTED) return false;
		
		if (_reciever != null) _reciever.cancel();
		if (_sender != null) _sender.cancel();
		
		_handler.post(new Runnable() {
			@Override
			public void run() {
				onIOClosed();
			}
		});
		return true;
	}
	
	protected boolean close() {
		if (Looper.myLooper() != _handler.getLooper()) return false;
		if (_socket_state != ConnectionState.CS_UNINITIALIZED) return false;
		_looper.post(null, true);
		return true;
	}
	
	protected boolean sendPacket(byte[] msg) {
		if (Looper.myLooper() != _handler.getLooper()) return false;
		if (_socket_state != ConnectionState.CS_CONNECTED) return false;
		_sender.send(msg, true);
		return true;
	}
	
	protected boolean sendLine(String line) {
		if (Looper.myLooper() != _handler.getLooper()) return false;
		if (_socket_state != ConnectionState.CS_CONNECTED) return false;
		_sender.send((line + "\n").getBytes(), false);
		return true;
	}
	
	protected ConnectionState getConnectionState() { return _socket_state; }
	
	private void onRecieverEnd() {
		_reciever = null;
		onIOClosed();
	}
	
	private void onSenderEnd() {
		_sender = null;
		onIOClosed();
	}
	
	private void onIOClosed() {
		if (_reciever == null && _sender == null && _socket_state == ConnectionState.CS_CONNECTED) {
			try {
				_socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			_socket = null;
			_socket_state = ConnectionState.CS_UNINITIALIZED;
			onConnectionState(false);
		}
	}
	
	protected void onPacket(byte[] msg) { }
	protected void onLine(String line) { }
	protected void onConnectionState(boolean connected) { }
}
