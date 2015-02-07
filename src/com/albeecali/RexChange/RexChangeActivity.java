package com.albeecali.RexChange;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class RexChangeActivity extends Activity {

	ArrayAdapter<CharSequence> adapter;
	Thread thread;
	String textViewText;
	String textViewCalcText;
	String curFrom = "USD";
	String curTo = "USD";
	TextView textView;
	TextView textViewRate;
	Button button;
	Spinner spinnerTo;
	Spinner spinnerFrom;
	EditText editText;
	DataBaseHelper dbHelper;
	SQLiteDatabase db;
	Double theRate;
	NumberFormat numberFormat;
	int theKey;
	int theInverseKey;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.main);
	    init();
	}
	
	private void init(){
    	dbHelper = new DataBaseHelper(this);
        try {
        	dbHelper.createDataBase();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        theRate = (double) 0;
    	numberFormat = new DecimalFormat("#,##0.00");
        dbHelper.openDataBase();
        db = dbHelper.getReadableDatabase();
	    textView = (TextView) findViewById(R.id.textView1);
	    textViewRate = (TextView) findViewById(R.id.textRate);
	    button = (Button) findViewById(R.id.buttonDisplay);
	    editText = (EditText) findViewById(R.id.editText1);
        adapter = ArrayAdapter.createFromResource(
                this, R.array.currencies, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTo = (Spinner) findViewById(R.id.spinnerTo);
        spinnerTo.setAdapter(adapter);
        spinnerTo.setOnItemSelectedListener(new OnItemSelectedListenerTo());
        spinnerFrom = (Spinner) findViewById(R.id.spinnerFrom);
        spinnerFrom.setAdapter(adapter);
        spinnerFrom.setOnItemSelectedListener(new OnItemSelectedListenerFrom());
        editText.addTextChangedListener(new TextWatcher() {
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				// TODO Auto-generated method stub
				if (theRate > 0) {
					try {
		            	double startAmt = Double.parseDouble(editText.getText().toString());
		            	textView.setText(numberFormat.format(Double.valueOf(theRate * startAmt)));
					} catch (NumberFormatException e) {
					}
				}
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void afterTextChanged(Editable s) {
				// TODO Auto-generated method stub
				
			}
		});
	}
	
	public void clearResults(){
		textView.setText("");
		textViewRate.setText("");
//		theRate = (double) 0;
	}
	
	
	public class OnItemSelectedListenerFrom implements OnItemSelectedListener {

	    public void onItemSelected(AdapterView<?> parent,
	        View view, int pos, long id) {
	      clearResults();
	      curFrom = parent.getItemAtPosition(pos).toString().substring(0, 3);
	    }

	    public void onNothingSelected(AdapterView parent) {
	      // Do nothing.
	    }
	}	
	
	public class OnItemSelectedListenerTo implements OnItemSelectedListener {

	    public void onItemSelected(AdapterView<?> parent,
	        View view, int pos, long id) {
	      clearResults();
	      curTo = parent.getItemAtPosition(pos).toString().substring(0, 3);
	    }

	    public void onNothingSelected(AdapterView parent) {
	      // Do nothing.
	    }
	}	
	
	public void doSwap(View view){
		int posFrom = spinnerFrom.getSelectedItemPosition();
		int posTo = spinnerTo.getSelectedItemPosition();
		spinnerTo.setSelection(posFrom);
		spinnerFrom.setSelection(posTo);
		if(theRate > 0)
		{
			theRate = 1/theRate;
	    	double startAmt = Double.parseDouble(editText.getText().toString());
	    	textView.setText(numberFormat.format(Double.valueOf(theRate * startAmt)));
	    	textViewRate.setText(String.format("Exchange Rate: %.4f",Double.valueOf(theRate)));
		}
	}
	
	public void doCallSvc(View view){
        Cursor result = db.rawQuery("SELECT _id , rate, time_stamp FROM rates "
        		+ " WHERE exc_from = '" + curFrom + "'"
        		+ " AND exc_to = '" + curTo + "'", null);
        
        if(result.getCount() > 0){
            result.moveToFirst();
    		long now = System.currentTimeMillis();
            long then = result.getLong(2);
            theKey = result.getInt(0);
            theRate = result.getDouble(1);
    		//elapsed time = 1000 milliseconds * 60 secs/min * 60 mins/hour = 3600000
            if((now - then) > 3600000)
            {
        		thread = new Thread(doHttpThread);
        		thread.start();
        		textViewRate.setText("Retrieving rates...");
            }
            else
            {
            	if(editText.getText().length()>0){
	            	double startAmt = Double.parseDouble(editText.getText().toString());
	            	textView.setText(numberFormat.format(Double.valueOf(theRate * startAmt)));
            	}
            	else {
					textView.setText("");
				}
            	textViewRate.setText(String.format("Exchange Rate: %.4f",Double.valueOf(theRate)));
            }
        }
        else
        {
    		thread = new Thread(doHttpThread);
    		thread.start();
    		textViewRate.setText("Retrieving rates...");
        }
        result.close();

	}
	
	public void updateRateTable(){
		long now = System.currentTimeMillis();
		double theInverseRate = 0;
		if(theRate > 0)theInverseRate = 1/theRate;
        Cursor result = db.rawQuery("SELECT _id FROM rates "
        		+ " WHERE exc_from = '" + curTo + "'"
        		+ " AND exc_to = '" + curFrom + "'", null);
        if(result.getCount() > 0){
            result.moveToFirst();
            theInverseKey = result.getInt(0);
        }
        result.close();
		ContentValues cv = new ContentValues();
		cv.put("rate", theRate);
		cv.put("time_stamp", now);
		db.update("rates", cv, "_id = " + theKey, null);
		
		cv = new ContentValues();
		cv.put("rate", theInverseRate);
		cv.put("time_stamp", now);
		db.update("rates", cv, "_id = " + theInverseKey, null);

	}
	
	//Assign this handler with the queue for the current thread
	// NOTE: the current thread here is the GUI thread
	Handler handler = new Handler();
	
	Runnable updateGUI = new Runnable() {
		
		public void run() {
			textViewRate.setText(textViewText);
			textView.setText(textViewCalcText);
		}
	};
	
    Runnable doHttpThread = new Runnable() {
 		public void run() {
 			//http://www.webservicex.net/CurrencyConvertor.asmx?WSDL
 			//GET /CurrencyConvertor.asmx/ConversionRate?FromCurrency=string&ToCurrency=string HTTP/1.1
 			//		Host: www.webservicex.net

 			HttpClient client = new DefaultHttpClient();
 			String url = "http://" + "www.webservicex.net" + 
 				"/CurrencyConvertor.asmx/ConversionRate?FromCurrency=" + curFrom +
 				"&ToCurrency=" + curTo;

    		HttpGet get = new HttpGet(url);
    		try {
				HttpResponse response = client.execute(get);
				int statusCode = response.getStatusLine().getStatusCode();
				if (statusCode == 200) {
					HttpEntity entity = response.getEntity();
		            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		            DocumentBuilder db;
					try {
						db = dbf.newDocumentBuilder();
			            Document doc = db.parse(entity.getContent());
			            if (doc != null) {
			                NodeList resultNodes = doc.getElementsByTagName("double");           
			                Node resultNode = resultNodes.item(0);           
			                double result = Double.parseDouble(resultNode.getTextContent());
			                if (result == 0) {
			        			textViewText = "Rate is Unavailable";
							}
			                else {
				                double startAmt = 1;
				        		try {
				        			startAmt = Double.parseDouble(editText.getText().toString());
				        		} catch (NumberFormatException nfe) {
				        			textViewText = "Rate is Unavailable";
				        		}
				        		theRate = result;
				        		updateRateTable();
				                textViewText = String.format("Exchange Rate: %.4f",Double.valueOf(result));
				                if (editText.getText().toString().length()>0) {
					                textViewCalcText = numberFormat.format(Double.valueOf(theRate * startAmt));
								}
				                else {
					                textViewCalcText = "";
								}
							}
						}
			            else {
							textViewText = ("doc is null"); 
						}
					} catch (ParserConfigurationException e) {
						textViewText = "ParserConfigurationException: " + e.getLocalizedMessage(); 
					} catch (IllegalStateException e) {
						textViewText = ("IllegalStateException: " + e.getLocalizedMessage()); 
					} catch (SAXException e) {
						textViewText = ("IllegalStateException: " + e.getLocalizedMessage()); 
					}
				}
			} catch (ClientProtocolException e) {
				textViewText = ("ClientProtocolException: " + e.getLocalizedMessage()); 
			} catch (IOException e) {
				textViewText = ("IOException: " + e.getLocalizedMessage()); 
			}
			handler.post(updateGUI);
		}
	};


}
