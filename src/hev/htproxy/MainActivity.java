/* MainActivity.java
 * Heiher <r@hev.cc>
 */

package hev.htproxy;

import java.util.Set;
import java.util.HashSet;
import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Scroller;
import android.text.method.ScrollingMovementMethod;
import android.view.View;

public class MainActivity extends Activity implements View.OnClickListener
{
	private Preferences prefs;
	private EditText edittext_server_address;
	private EditText edittext_server_port;
	private EditText edittext_bypass_addresses;
	private EditText edittext_applications;
	private Button button_restart;
	private Button button_control;
	private Messenger mSocks5Service = null;
	private Messenger mTProxyService = null;
	private Messenger mDNSFwdService = null;

	private ServiceConnection mSocks5Connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mSocks5Service = new Messenger(service);
		}

		public void onServiceDisconnected(ComponentName className) {
			mSocks5Service = null;
		}
	};
	private ServiceConnection mTProxyConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mTProxyService = new Messenger(service);
		}

		public void onServiceDisconnected(ComponentName className) {
			mTProxyService = null;
		}
	};
	private ServiceConnection mDNSFwdConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mDNSFwdService = new Messenger(service);
		}

		public void onServiceDisconnected(ComponentName className) {
			mDNSFwdService = null;
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		prefs = new Preferences(getApplicationContext());
		setContentView(R.layout.main);

		edittext_server_address = (EditText) findViewById(R.id.server_address);
		edittext_server_port = (EditText) findViewById(R.id.server_port);
		edittext_bypass_addresses = (EditText) findViewById(R.id.bypass_addresses);
		edittext_applications = (EditText) findViewById(R.id.applications);
		button_restart = (Button) findViewById(R.id.restart);
		button_control = (Button) findViewById(R.id.control);

		edittext_server_address.setText(prefs.getServerAddress());
		edittext_server_port.setText(Integer.toString(prefs.getServerPort()));
		edittext_bypass_addresses.setScroller(new Scroller(this));
		edittext_bypass_addresses.setMaxLines(10);
		edittext_bypass_addresses.setVerticalScrollBarEnabled(true);
		edittext_bypass_addresses.setMovementMethod(new ScrollingMovementMethod());
		StringBuilder builder = new StringBuilder();
		for (String addr : prefs.getBypassAddresses()) {
			builder.append(addr);
			builder.append("\n");
		}
		edittext_bypass_addresses.setText(builder.toString());
		edittext_applications.setScroller(new Scroller(this));
		edittext_applications.setMaxLines(10);
		edittext_applications.setVerticalScrollBarEnabled(true);
		edittext_applications.setMovementMethod(new ScrollingMovementMethod());
		builder = new StringBuilder();
		for (String app : prefs.getApplications()) {
			builder.append(app);
			builder.append("\n");
		}
		edittext_applications.setText(builder.toString());
		button_restart.setOnClickListener(this);
		button_control.setOnClickListener(this);

		/* socks5 service */
		Intent i = new Intent(getApplicationContext(), Socks5Service.class);
		getApplicationContext().startService(i);
		/* tproxy service */
		i = new Intent(getApplicationContext(), TProxyService.class);
		getApplicationContext().startService(i);
		/* dns fwd service */
		i = new Intent(getApplicationContext(), DNSFwdService.class);
		getApplicationContext().startService(i);

		/* is Supported */
		button_control.setEnabled(false);
		if (RedirectManager.isSupported())
		  button_control.setEnabled(true);
		else
		  prefs.setHTProxyEnabled(false);
		/* is Enabled */
		boolean redir_enabled = RedirectManager.isEnabled(getApplicationContext());
		if (prefs.getHTProxyEnabled()) {
			if (!redir_enabled) {
				if (RedirectManager.setEnabled(true, getApplicationContext()))
				  redir_enabled = true;
			}
		} else {
			if (redir_enabled) {
				if (RedirectManager.setEnabled(false, getApplicationContext()))
				  redir_enabled = false;
			}
		}
		lockUI(redir_enabled);
	}

	@Override
	protected void onStart() {
		super.onStart();

		bindService(new Intent(this, Socks5Service.class), mSocks5Connection, Context.BIND_AUTO_CREATE);
		bindService(new Intent(this, TProxyService.class), mTProxyConnection, Context.BIND_AUTO_CREATE);
		bindService(new Intent(this, DNSFwdService.class), mDNSFwdConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		unbindService(mDNSFwdConnection);
		unbindService(mTProxyConnection);
		unbindService(mSocks5Connection);

		super.onStop();
	}

	@Override
	protected void onDestroy() {
		savePrefs();

		super.onDestroy();
	}

	public void onClick(View view) {
		if (view == button_restart) {
			stopDNSFwdService();
			stopTProxyService();
			stopSocks5Service();
			savePrefs();
			startSocks5Service();
			startTProxyService();
			startDNSFwdService();
		} else if (view == button_control) {
			boolean redir_enabled = RedirectManager.isEnabled(getApplicationContext());
			if (redir_enabled) {
				if (RedirectManager.setEnabled(false, getApplicationContext()))
				  redir_enabled = false;
			} else {
				savePrefs();
				if (RedirectManager.setEnabled(true, getApplicationContext()))
				  redir_enabled = true;
			}
			lockUI(redir_enabled);
			prefs.setHTProxyEnabled(redir_enabled);
		}
	}

	private void lockUI(boolean lock) {
		if (lock)
		  button_control.setText(R.string.control_disable);
		else
		  button_control.setText(R.string.control_enable);
		edittext_server_address.setEnabled(!lock);
		edittext_server_port.setEnabled(!lock);
		edittext_bypass_addresses.setEnabled(!lock);
		edittext_applications.setEnabled(!lock);
	}

	private void savePrefs() {
		String[] addrs, apps;
		Set<String> bypass_addresses = new HashSet<String>();
		Set<String> applications = new HashSet<String>();

		prefs.setServerAddress(edittext_server_address.getText().toString());
		prefs.setServerPort(Integer.parseInt(edittext_server_port.getText().toString()));

		addrs = edittext_bypass_addresses.getText().toString().split("\n");
		for (String addr : addrs) {
			if (!addr.isEmpty())
			  bypass_addresses.add(addr);
		}
		prefs.setBypassAddresses(bypass_addresses);

		apps = edittext_applications.getText().toString().split("\n");
		for (String app : apps ) {
			if (!app.isEmpty())
			  applications.add(app);
		}
		prefs.setApplications(applications);
	}

	private void startSocks5Service() {
		if (null == mSocks5Service)
		  return;

		try {
			Message msg = Message.obtain(null, Socks5Service.MessageHandler.TYPE_START);
			mSocks5Service.send(msg);
		} catch (RemoteException e) {
		}
	}

	private void stopSocks5Service() {
		if (null == mSocks5Service)
		  return;

		try {
			Message msg = Message.obtain(null, Socks5Service.MessageHandler.TYPE_STOP);
			mSocks5Service.send(msg);
		} catch (RemoteException e) {
		}
	}

	private void startTProxyService() {
		if (null == mTProxyService)
		  return;

		try {
			Message msg = Message.obtain(null, TProxyService.MessageHandler.TYPE_START);
			mTProxyService.send(msg);
		} catch (RemoteException e) {
		}
	}

	private void stopTProxyService() {
		if (null == mTProxyService)
		  return;

		try {
			Message msg = Message.obtain(null, TProxyService.MessageHandler.TYPE_STOP);
			mTProxyService.send(msg);
		} catch (RemoteException e) {
		}
	}

	private void startDNSFwdService() {
		if (null == mDNSFwdService)
		  return;

		try {
			Message msg = Message.obtain(null, DNSFwdService.MessageHandler.TYPE_START);
			mDNSFwdService.send(msg);
		} catch (RemoteException e) {
		}
	}

	private void stopDNSFwdService() {
		if (null == mDNSFwdService)
		  return;

		try {
			Message msg = Message.obtain(null, DNSFwdService.MessageHandler.TYPE_STOP);
			mDNSFwdService.send(msg);
		} catch (RemoteException e) {
		}
	}
}
