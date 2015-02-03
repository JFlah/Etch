package me.etch.etchapp.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import com.pubnub.api.Callback;
import com.pubnub.api.Pubnub;
import com.pubnub.api.PubnubError;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import me.etch.etchapp.R;
import me.etch.etchapp.adt.DrawPoint;
import me.etch.etchapp.adt.EtchUser;
import me.etch.etchapp.util.Config;

/**
 * Created by GleasonK on 1/25/15.
 */
public class DrawingView extends View {
    public Pubnub pubnub;
    private Map<String,EtchUser> remoteUsers;
    private Path remotePath;
    private Paint remotePaint;
    private List<DrawPoint> publishPoints;
    private Set<Integer> usedColors;

    String AUTH_KEY;
    String UUID;

    public static final long DRAW_TIME = 25 * 100;
    private Path drawPath;
    private Paint drawPaint, eraserPaint, canvasPaint;
    private int drawColor;
    private Canvas drawCanvas;
    private Bitmap canvasBitmap;

    private float brushSize;

    public int[] colors = {
            R.color.blue,
            R.color.green,
            R.color.orange,
            R.color.pink,
            R.color.red,
            R.color.yellow,
            R.color.white
    };

    public DrawingView(Context context){
        super(context);
        setupDrawing();
        setupRemoteDrawing();
        setupErasing();
        initPubNub();
        subscribe();

    }

    public DrawingView(Context context, AttributeSet attributeSet){
        super(context, attributeSet);
        setupDrawing();
        setupRemoteDrawing();
        setupErasing();
        initPubNub();
        subscribe();
    }

    private void setupDrawing(){
        this.usedColors = new HashSet<Integer>();
        this.publishPoints = new ArrayList<DrawPoint>();

        drawPath = new Path();
        drawPaint = new Paint();
        int colorID = 0;
        drawColor = getResources().getColor(colors[colorID]);
        this.usedColors.add(0);
        drawPaint.setColor(drawColor);
        setBrushSize(10);
        drawPaint.setStrokeWidth(this.brushSize);

        // Initial path properties
        drawPaint.setAntiAlias(true);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        //Makes it appear smoother
        canvasPaint = new Paint(Paint.DITHER_FLAG);
    }

    private void setupRemoteDrawing(){
        remoteUsers = new HashMap<String, EtchUser>();

        remotePath = new Path();
        // Draw paint will hold the color.
        remotePaint = new Paint();
        int remoteColor = chooseColor();
        remotePaint.setColor(remoteColor);
        remotePaint.setStrokeWidth(this.brushSize);
        // Initial path properties
        remotePaint.setAntiAlias(true);
        remotePaint.setStyle(Paint.Style.STROKE);
        remotePaint.setStrokeJoin(Paint.Join.ROUND);
        remotePaint.setStrokeCap(Paint.Cap.ROUND);
        this.remoteUsers.put(UUID,new EtchUser(remotePath, remotePaint, remoteColor));
    }

    private void setupRemoteUser(String UUID){
        Path remotePath = new Path();
        // Draw paint will hold the color.
        Paint remotePaint = new Paint();
        int remoteColor = chooseColor();
        remotePaint.setColor(remoteColor);
        remotePaint.setStrokeWidth(this.brushSize);
        // Initial path properties
        remotePaint.setAntiAlias(true);
        remotePaint.setStyle(Paint.Style.STROKE);
        remotePaint.setStrokeJoin(Paint.Join.ROUND);
        remotePaint.setStrokeCap(Paint.Cap.ROUND);
        this.remoteUsers.put(UUID,new EtchUser(remotePath, remotePaint, remoteColor));
    }

    private void setupErasing(){
        eraserPaint = new Paint();
        eraserPaint.setColor(Color.TRANSPARENT);

        eraserPaint.setStrokeWidth(this.brushSize + 10);
        // Initial path properties

        eraserPaint.setAntiAlias(true);
        eraserPaint.setStyle(Paint.Style.STROKE);
        eraserPaint.setStrokeJoin(Paint.Join.ROUND);
        eraserPaint.setStrokeCap(Paint.Cap.ROUND);
        //Makes it appear smoother
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH){
        super.onSizeChanged(w, h, oldW, oldH);
        canvasBitmap = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        drawCanvas = new Canvas(canvasBitmap);
    }

    @Override
    protected void onDraw(Canvas canvas){
        canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
        canvas.drawPath(drawPath, drawPaint);
        canvas.drawPath(remotePath, remotePaint);
//        for(EtchUser eu : this.remoteUsers.values()){
//            canvas.drawPath(eu.getPath(), eu.getPaint());
//        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        float touchX = event.getX();
        float touchY = event.getY();

        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                publishWithThreshold(touchX, touchY, Config.PUB_ACTION_DOWN);
                drawPath.moveTo(touchX, touchY);
                drawPath.lineTo(touchX+0.01f,touchY+0.01f);
                break;
            case MotionEvent.ACTION_MOVE:
                publishWithThreshold(touchX, touchY, Config.PUB_ACTION_MOVE);
                drawPath.lineTo(touchX, touchY);
                break;
            case MotionEvent.ACTION_UP:
                publishWithThreshold(touchX, touchY, Config.PUB_ACTION_UP);
                drawCanvas.drawPath(drawPath, drawPaint);
                fadeOut(drawPath); //TODO Handler to delete.
                drawPath = new Path();
                drawPath.reset();
                break;
            default:
                return false;
        }
        invalidate();  //TODO: Will have to invalidate after push notification
        return true;
    }

    public void setBrushSize(float newSize){
        float pixelAmount = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newSize, getResources().getDisplayMetrics());
        this.brushSize = pixelAmount;
    }

    public void fadeOut(final Path path){
        Thread fadeRunner = new Thread(){

            @Override
            public void run() {
                int i = 0;
                try { Thread.sleep(DRAW_TIME); } catch ( InterruptedException e) { e.printStackTrace(); }

                while (i < 100){
                    if (doFade(path,i) >= 40)
                        break;
                    i++;
                    try { Thread.sleep(20); } catch ( InterruptedException e) { e.printStackTrace(); }
                }
                doRemoval(path);
            }
        };
        fadeRunner.start();
    }

    public int doFade(Path path, int iter){
        //Removal
        int alpha = iter;
        eraserPaint.setAlpha(alpha);
        drawCanvas.drawPath(path, eraserPaint);
        postInvalidate();
        return alpha;
    }

    public void doRemoval(Path path){
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        //Removal
        eraserPaint.setColor(Color.TRANSPARENT);
        drawCanvas.drawPath(path, eraserPaint);

        //Cleanup
        eraserPaint.setXfermode(null);
        ColorDrawable cd = (ColorDrawable) getBackground();
        eraserPaint.setColor(cd.getColor());

        postInvalidate();
    }

    public JSONArray jsonArrayPoints() throws JSONException{
        JSONArray jArray = new JSONArray();
        for(DrawPoint dp : this.publishPoints){
            jArray.put(dp.toString());
        }
        return jArray;
    }

    public void initPubNub(){
        this.pubnub = new Pubnub(
                Config.PUBLISH_KEY,
                Config.SUBSCRIBE_KEY,
                Config.SECRET_KEY,
                Config.CIPHER_KEY,
                Config.SSL
        );
        this.pubnub.setCacheBusting(false);
        this.pubnub.setOrigin(Config.ORIGIN);
        this.pubnub.setAuthKey(AUTH_KEY);
        setHeartbeat(10);
        this.UUID = this.pubnub.getUUID();
    }

    public void setHeartbeat(int hb){
        pubnub.setHeartbeat(hb);
    }

    public void publishWithThreshold(float touchX, float touchY, String action){
        if (action.equals(Config.PUB_ACTION_DOWN)){
            this.publishPoints.add(new DrawPoint(touchX,touchY));
            _publishWithThreshold(action);
        }
        else if (action.equals(Config.PUB_ACTION_MOVE)){
            this.publishPoints.add(new DrawPoint(touchX, touchY));
            if (publishPoints.size() > Config.PUB_THRESHOLD)
                _publishWithThreshold(action);
        }
        else if (action.equals(Config.PUB_ACTION_UP)){
            if (publishPoints.size() > 0)
                _publishWithThreshold(Config.PUB_ACTION_MOVE); //Clear moving data.
            this.publishPoints.add(new DrawPoint(touchX, touchY));
            _publishWithThreshold(action);
        }
    }

    private void _publishWithThreshold(String action){
        JSONObject js = new JSONObject();
        try {
            System.out.println(jsonArrayPoints());
            js.put("pathCoords", jsonArrayPoints());
            js.put("action", action);
            js.put("UUID",this.UUID);
        } catch (JSONException e) { e.printStackTrace(); }

        Callback publishCallback = new Callback() {
            @Override
            public void successCallback(String channel, Object message) {
                notifyUser("PUBLISH : " + message);
            }

            @Override
            public void errorCallback(String channel, PubnubError error) {
                notifyUser("PUBLISH : " + error);
            }
        };
        pubnub.publish(Config.CHANNEL, js, publishCallback);

        this.publishPoints.clear();
    }

    public void publish(Object message, String action){
        JSONObject js = new JSONObject();
        try {
            js.put("message", message);
            js.put("action", action);
        } catch (JSONException e) { e.printStackTrace(); }

        Callback publishCallback = new Callback() {
            @Override
            public void successCallback(String channel, Object message) {
                notifyUser("PUBLISH : " + message);
            }

            @Override
            public void errorCallback(String channel, PubnubError error) {
                notifyUser("PUBLISH : " + error);
            }
        };
        pubnub.publish(Config.CHANNEL, js, publishCallback);
    }


    public void subscribe(){
        try {
            pubnub.subscribe(Config.CHANNEL, new Callback() {
                @Override
                public void connectCallback(String channel, Object message) {
                    notifyUser("SUBSCRIBE : CONNECT on channel:"
                            + channel + " : " + message.getClass() + " : " + message.toString());
                }

                @Override
                public void disconnectCallback(String channel, Object message) {
                    notifyUser("SUBSCRIBE : DISCONNECT on channel:"
                            + channel + " : " + message.getClass() + " : " + message.toString());
                }

                @Override
                public void reconnectCallback(String channel, Object message) {
                    notifyUser("SUBSCRIBE : RECONNECT on channel:"
                            + channel + " : " + message.getClass() + " : " + message.toString());
                }

                @Override
                public void successCallback(String channel, Object message) {
                    notifyUser("SUBSCRIBE : " + channel + " : " + message.getClass() + " : " + message.toString());
                    dispatchPubNubMessage(message);
                }

                @Override
                public void errorCallback(String channel, PubnubError error) {
                    notifyUser("SUBSCRIBE : ERROR on channel " + channel + " : " + error.toString());
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void dispatchPubNubMessage(Object message) {
        try {
            if (message instanceof JSONObject) {
                JSONObject jsonObject = (JSONObject) message;

                JSONArray pointArray = jsonObject.getJSONArray("pathCoords");
                String action = jsonObject.getString("action");
                String otherUUID = jsonObject.getString("UUID");
                if (!this.UUID.equals(otherUUID)) {
                    if (!this.remoteUsers.containsKey(otherUUID)){ setupRemoteUser(otherUUID); }
                    EtchUser otherUser = this.remoteUsers.get(otherUUID);
                    for (int i = 0; i < pointArray.length(); i++) {
                        String s = pointArray.getString(i);
                        String[] pts = s.split(":");
                        float xCoord = Float.parseFloat(pts[0]);
                        float yCoord = Float.parseFloat(pts[1]);
                        dispatchDrawPath(xCoord, yCoord, action);
                    }
                }
//                double xCoord = jsonObject.getDouble("xCoord");
//                double yCoord = jsonObject.getDouble("yCoord");
//                dispatchDrawPath((float)xCoord, (float)yCoord, action);
            } else if (message instanceof String) {
                String stringMessage = (String) message;
            } else if (message instanceof JSONArray) {
                JSONArray jsonArray = (JSONArray) message;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dispatchDrawPath(float touchX, float touchY, String action){
        if (action.equals(Config.PUB_ACTION_DOWN)){
            remotePath.moveTo(touchX, touchY);
            remotePath.lineTo(touchX+0.1f,touchY+0.1f);
        }
        else if (action.equals(Config.PUB_ACTION_MOVE)){
            remotePath.lineTo(touchX,touchY);
        }
        else if (action.equals(Config.PUB_ACTION_UP)){
            drawCanvas.drawPath(remotePath, remotePaint);
            fadeOut(remotePath);
            remotePath = new Path();
            remotePath.reset();
        }
        postInvalidate();
    }

    private int chooseColor(){
        Random r = new Random();
        int colorID = (r.nextInt(this.colors.length-1)+1 % this.colors.length);
        while (this.usedColors.size() < this.colors.length && this.usedColors.contains(colorID)){
            colorID=(colorID+1)%this.colors.length;
        }
        return getResources().getColor(colors[colorID]);
    }

    private void notifyUser(Object message) {
        try {
            if (message instanceof JSONObject) {
                final JSONObject obj = (JSONObject) message;
                Log.i("Received msg : ", String.valueOf(obj));
            }
            else if (message instanceof String) {
                final String obj = (String) message;
                Log.i("Received msg : ", obj.toString());
            }
            else if (message instanceof JSONArray) {
                final JSONArray obj = (JSONArray) message;
                Log.i("Received msg : ", obj.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
