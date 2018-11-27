package hygunth.dynamicringer;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Button;
import android.widget.TextView;
import android.media.AudioManager;
import android.widget.ToggleButton;
import android.widget.EditText;

import org.w3c.dom.Text;

import static android.telephony.TelephonyManager.CALL_STATE_RINGING;
import static java.lang.Math.log;
import static java.lang.Math.pow;

public class MainActivity extends AppCompatActivity {
    private Button button;
    private EditText editText;

    private ToggleButton activateButton;
    private TextView activateButtonLabel;
    private TextView ring_text;

    private CheckBox enabledBox;
    private boolean enabledChecked = false;
    private AudioRecord recorder = null;
    private AudioManager ringer = null;
    private TelephonyManager telephonyManager = null;
    private static final int RECORDER_SAMPLE_RATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_FLOAT;
    private float audioData[];
    private float noiseLevel;
    private int volumeLevel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Permissions
        if(permissionsCheck() == false){
            requestPermissions();
        }

        // Initialize variables
        ringer = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioData = new float[RECORDER_SAMPLE_RATE];
        enabledBox = (CheckBox)findViewById(R.id.enable_check);

        // Interaction listeners
        enabledBox.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                enabledChecked = !enabledChecked;
                if(enabledChecked){
                    int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLE_RATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            RECORDER_SAMPLE_RATE,
                            RECORDER_CHANNELS,
                            RECORDER_AUDIO_ENCODING,
                            bufferSize);
                    if(recorder.getState() != AudioRecord.STATE_INITIALIZED){
                        throw new IllegalStateException("MediaRecorder.AudioSource.MIC did not report STATE_INITIALIZED");
                    }
                    telephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
                    telephonyManager.listen(new MyPhoneListener(), PhoneStateListener.LISTEN_CALL_STATE);
                }else{
                    recorder.release();
                    recorder = null;
                    telephonyManager.listen(new MyPhoneListener(), PhoneStateListener.LISTEN_NONE);
                    telephonyManager = null;
                }
            }
        });

    }

    protected boolean permissionsCheck(){
        // Returns true if we have all the permissions we need
        if(ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            return false;
        }
        else{
            return true;
        }
    }

    protected void requestPermissions(){
        // Requests all the permissions we need
        // ToDo: Display text explaining what permissions we are about to ask for and why
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO },0);
    }

    protected void setVolumeLevel(int level){
        if(level <= 0){
            ringer.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
            //ringer.setVibrateSetting (int vibrateType, int vibrateSetting)
        } else if(level < 6){
            ringer.setStreamVolume(AudioManager.STREAM_RING, level, 0);
        }
        else{
            ringer.setStreamVolume(AudioManager.STREAM_RING, 7, 0);
        }
    }

    protected double mic2db(double x){
        return 11.086 * log(x) + 119.77;
    }

    protected int db2Vol(double x){
        return (int)(8*pow(10, -5)*pow(x, 3) -0.0142 * pow(x,2) + 0.9535 * x + -20.494);
    }

    protected int translate(double x) {
        double level = mic2db(x);
        if (level <= 10) return 1;
        else if (level < 25) return 1;
        else if (level < 50) return 2;
        else if (level < 60) return 3;
        else if (level < 70) return 4;
        else if (level < 80) return 5;
        else if (level < 90) return 6;
        else return 7;
    }

    protected class StartAmbientNoise extends AsyncTask<Void, Integer, Float> {
        protected Float doInBackground(Void... thing){
            recorder.startRecording();
            recorder.read(audioData, 0, audioData.length, AudioRecord.READ_BLOCKING);
            recorder.stop();
            float sum = 0;
            noiseLevel = 100000.0f;
            int chunk = audioData.length/10;
            float maybeNoiseLevel = 0;
            for(int i = 0; i < 5; i++){
                for(int j = 0; j < chunk; j++){sum += Math.abs(audioData[j]);}
                maybeNoiseLevel = sum/chunk;
                if(maybeNoiseLevel < noiseLevel) noiseLevel = maybeNoiseLevel;
            }
            volumeLevel = translate(noiseLevel);
            setVolumeLevel(volumeLevel);
            System.out.println("Noise: " + Double.toString(mic2db(noiseLevel)));
            System.out.println("Volum: " + Integer.toString(volumeLevel));
            return noiseLevel;
        }
    }

    protected class MyPhoneListener extends PhoneStateListener{
        @Override
        public void onCallStateChanged(int state, String incomingNumber){
            if(state == TelephonyManager.CALL_STATE_RINGING){
                StartAmbientNoise task = new StartAmbientNoise();
                task.execute();
            }
        }
    }
}