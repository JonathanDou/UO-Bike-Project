package com.example.jonat.test

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.location.LocationListener
import android.location.Location
import android.location.LocationManager
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage
import android.app.PendingIntent
import android.content.Intent
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import org.json.JSONArray
import java.util.*
import org.json.JSONObject
import java.text.SimpleDateFormat

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    var timestamp = 0L

    var speedcommand = "NO SPEED YET"

    val bike_speeds = arrayOf(2.0F, 8.0F)
    var timestamps: Array<Long> = arrayOf(0,0,0,0,0,0,0)

    var redtimes = arrayOf(34F,34F,34F,34F,34F,34F,34F)
    var greentimes = arrayOf(38F,38F,38F,38F,38F,38F,38F)

    var active_phases = ""
    var timestampstring = ""


    //MQTT

    val serverUri = "tcp://73.240.57.56:4820"
    val clientId = "bjadbwqjadasldjwoadkasd"
    val username = "sycgirpm"
    val password = "GuSZRRN0D4tH"

    fun find_speed(speeds: Array<Float>, distance: Float, initial_color: String, times: Array<Float>): Pair<Float, Float> {
        var minspeed = 0.0F
        var maxspeed = 0.0F
        var speed = speeds[0]
        var time = 0.0F

        var foundmax = false

        var color1 = 0
        var color2 = 0

        if(initial_color == "r") {
            color2 = 1
        } else {
            color1 = 1
        }

        while(speed <= speeds[1]) {
            time = distance/speed

            if(time < times[2]) {
                if(initial_color == "g") {
                    //Log.d("SPEEDTAG", "No stop at speed: " + speed + " Time: " + t1)
                    if(minspeed == 0.0F) {
                        minspeed = speed
                        maxspeed = speed
                    } else {
                        maxspeed = speed
                    }
                }
            } else {

                time = time - times[2]

                while(true) {
                    if((time - times[color1]) < 0) {
                        if(color1 == 0) {
                            //Log.d("SPEEDTAG", "No stop at speed: " + speed + " Time: " + t1)
                            if(minspeed == 0.0F || foundmax == true) {
                                minspeed = speed
                                foundmax = false
                            }
                            maxspeed = speed
                        } else {
                            if(minspeed != 0.0F && foundmax == false) {
                                foundmax = true
                            }
                        }
                        break
                    } else {
                        time = time - times[color1]
                    }

                    if((time - times[color2]) < 0) {
                        if(color2 == 0) {
                            //Log.d("SPEEDTAG", "No stop at speed: " + speed + " Time: " + t1)
                            if(minspeed == 0.0F || foundmax == true) {
                                minspeed = speed
                                foundmax = false
                            }
                            maxspeed = speed
                        } else {
                            if(minspeed != 0.0F && foundmax == false) {
                                foundmax = true
                            }
                        }
                        break
                    } else {
                        time = time - times[color2]
                    }
                }
            }

            speed = speed + 0.1F

        }
        //Log.d("SPEEDTAG2", "Min Speed: " + minspeed + "\nMax Speed: " + maxspeed)
        return Pair(minspeed, maxspeed)
    }

    fun appReady(): Boolean {
        for(i in 0..6) {
            if(timestamps[i] == 0L) {
                return false
            }
        }

        return true
    }

    fun getActivity(speed: Float, maxspeed: Float, on13th: Boolean): String {
        if(on13th == true) {
            if(maxspeed == 0F) {
                return "STILL"
            } else if (maxspeed < 2F) {
                return "WALKING"
            } else if (maxspeed < 8F) {
                return "BIKING"
            } else {
                return "DRIVING"
            }
        } else {
            if(speed == 0F) {
                return "STILL"
            } else if (speed < 2F) {
                return "WALKING"
            } else if (speed < 8F) {
                return "BIKING"
            } else {
                return "DRIVING"
            }
        }
    }

    fun parsePayload(message: String) {

        var jsobject = JSONObject(message)
        var timearray: JSONArray = jsobject.getJSONArray("AllPhases")
        var timeobject = timearray.getJSONObject(0)
        var rawts = timeobject.getString("BecameActiveTimestamp")
        timestampstring = rawts.toString()

        var dateFormat = SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSSSSSZ", Locale.UK)
        var parsedDate = dateFormat.parse(timestampstring)
        timestamp = parsedDate.getTime()

        if((System.currentTimeMillis() - timestamp) > 10000) {
            timestamp = timestamp + 12  *3600*1000
        }

        active_phases = jsobject.getString("ActivePhases")

    }


    override fun onInit(status: Int) {
        if(status == TextToSpeech.SUCCESS) {
            tts!!.setLanguage(Locale.US)
            Log.d("TTSTAG", "Text to Speech Initialized")
        } else {
            Log.d("TTSTAG", "TTS Failed to Initialize")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            System.exit(0)
        } else {
            val text = "Restart the App to Setup GPS Location! App will close in 3 seconds..."
            val duration = Toast.LENGTH_LONG

            val toast = Toast.makeText(applicationContext, text, duration)
            toast.show()

            Handler().postDelayed({
                System.exit(0)
            }, 3000)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTitle("Biker App")

        //Text To Speech

        tts = TextToSpeech(applicationContext, this)

        //Texts

        var dataReceived = findViewById(R.id.dataReceived) as TextView
        dataReceived.setText("Server Message: ")
        dataReceived.setTextSize(15f)

        var text = findViewById(R.id.my_text) as TextView
        text.setTextSize(20f)

        //Images

        var speedup = findViewById(R.id.imageup) as ImageView
        var slowdown = findViewById(R.id.imagedown) as ImageView
        var check = findViewById(R.id.imagecheck) as ImageView
        var speedup2 = findViewById(R.id.imageup2) as ImageView
        var slowdown2 = findViewById(R.id.imagedown2) as ImageView

        speedup.setImageResource(R.drawable.speedup)
        slowdown.setImageResource(R.drawable.slowdown)
        check.setImageResource(R.drawable.check)
        speedup2.setImageResource(R.drawable.smallup)
        slowdown2.setImageResource(R.drawable.smalldown)

        //Buttons

        var button = findViewById(R.id.button) as Button
        var button2 = findViewById(R.id.button2) as Button
        var button3 = findViewById(R.id.button3) as Button

        var trafficscreen = false

        text.visibility = View.GONE
        dataReceived.visibility = View.GONE
        speedup.visibility = View.GONE
        slowdown.visibility = View.GONE
        speedup2.visibility = View.GONE
        slowdown2.visibility = View.GONE
        check.visibility = View.GONE

        button.setOnClickListener {
            text.visibility = View.VISIBLE
            dataReceived.visibility = View.GONE
            speedup.visibility = View.GONE
            slowdown.visibility = View.GONE
            speedup2.visibility = View.GONE
            slowdown2.visibility = View.GONE
            check.visibility = View.GONE
            trafficscreen = false
        }

        button2.setOnClickListener {
            text.visibility = View.GONE
            dataReceived.visibility = View.VISIBLE
            speedup.visibility = View.GONE
            slowdown.visibility = View.GONE
            speedup2.visibility = View.GONE
            slowdown2.visibility = View.GONE
            check.visibility = View.GONE
            trafficscreen = false
        }

        button3.setOnClickListener {
            text.visibility = View.GONE
            dataReceived.visibility = View.GONE
            trafficscreen = true
        }


        //Traffic Lights

        var hilyard = Location("")
        hilyard.latitude = 44.045510
        hilyard.longitude = -123.082158

        var patterson = Location("")
        patterson.latitude = 44.045480
        patterson.longitude = -123.083542

        var high = Location("")
        high.latitude = 44.045501
        high.longitude = -123.088121

        var pearl = Location("")
        pearl.latitude = 44.045496
        pearl.longitude = -123.089650

        var oak = Location("")
        oak.latitude = 44.045497
        oak.longitude = -123.091167

        var willamette = Location("")
        willamette.latitude = 44.045512
        willamette.longitude = -123.092700

        var olive = Location("")
        olive.latitude = 44.045503
        olive.longitude = -123.094252

        var oldlocation = Location("")

        var signals = listOf(olive, willamette, oak, pearl, high, patterson, hilyard)
        var signal_names = listOf("olive", "willamette", "oak", "pearl", "high", "patterson", "hilyard")

        //CHECK PERMISSIONS

        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val permissions = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
            ActivityCompat.requestPermissions(this, permissions, 0)

            Log.d("PERMISSION TAG", "REQUESTED")
        } else {
            Log.d("PERMISSION TAG", "NOT REQUESTED")
        }

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        //Notification

        var mBuilder = NotificationCompat.Builder(this)
        mBuilder.setContentTitle("Biker Notification")
        mBuilder.setContentText("App Started")
        mBuilder.setSmallIcon(R.drawable.ic_stat_onesignal_default)
        mBuilder.setOngoing(true)
        mBuilder.setContentIntent(PendingIntent.getActivity(this , 100 ,
                Intent(this, MainActivity::class.java) , PendingIntent.FLAG_UPDATE_CURRENT))

        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.notify(1, mBuilder.build())

        //MQTT CLIENT

        var client = MqttAndroidClient(applicationContext, serverUri, clientId)


        //Check Location

        val locationListener: LocationListener = object : LocationListener {

            var targetlocation = Location("")
            var distance = 0.0F
            var androidspeed = 0.0F
            var nextstop = ""
            var activitytext = ""
            var currentstop = 0
            var currentlocation = Location("")
            var on13th = false
            var display = ""
            var maxspeed = 0F
            var initialcolor = "g"
            var counter = 0

            override fun onLocationChanged(location: Location) {

                if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    oldlocation.longitude = currentlocation.longitude

                    currentlocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

                    //check if on 13th

                    if(currentlocation.latitude > 44.045350 && currentlocation.latitude < 44.045750 && currentlocation.longitude > -123.095 && currentlocation.longitude < -123.081) {
                        if(on13th == false) {
                            on13th = true
                            mBuilder.setContentText("Tap Here to Go to App")
                            mNotificationManager.notify(1, mBuilder.build())
                            tts!!.speak("You are now on 13th Avenue. You will now get text to speech speed suggestions to avoid stops.", TextToSpeech.QUEUE_FLUSH, null)
                        }
                    } else {
                        on13th = false
                    }

                    //find next stop
                    if(androidspeed > 0.5) {
                        if(on13th == true) {
                            if(currentlocation.longitude < oldlocation.longitude) {
                                for(i in 1..(signals.size)) {
                                    if(signals[signals.size - i].longitude < currentlocation.longitude) {
                                        targetlocation = signals[signals.size - i]
                                        nextstop = signal_names[signals.size - i]
                                        currentstop = signals.size - i
                                        break
                                    }
                                }
                            } else {
                                for(i in 0..(signals.size - 1)) {
                                    if(signals[i].longitude > currentlocation.longitude) {
                                        targetlocation = signals[i]
                                        nextstop = signal_names[i]
                                        currentstop = i
                                        break
                                    }
                                }
                            }
                        } else {
                            nextstop = "None"
                        }
                    }


                    distance = currentlocation.distanceTo(targetlocation)
                    var timeremaining = (((System.currentTimeMillis() - timestamps[currentstop])/1000)%72).toFloat()

                    if(timeremaining < greentimes[currentstop]) {
                        initialcolor = "g"
                        timeremaining = greentimes[currentstop] - timeremaining
                    } else {
                        initialcolor = "r"
                        timeremaining = 72 - timeremaining
                    }


                    if(timeremaining < 0) {
                        timeremaining = 0F
                    }

                    var times = arrayOf(greentimes[currentstop],redtimes[currentstop],timeremaining) //green/red/initial

                    //find min and max speeds

                    var(min,max) = find_speed(bike_speeds, distance, initialcolor, times)

                    androidspeed = currentlocation.speed

                    if(on13th == true) {
                        if(maxspeed < androidspeed) {
                            maxspeed = androidspeed
                        }
                    } else {
                        maxspeed = 0F
                    }

                    //Suggest speed

                    if(on13th == true) {
                        if(androidspeed > max + 2) {
                            speedcommand = "SLOW DOWN"
                            check.visibility = View.GONE
                            speedup.visibility = View.GONE
                            slowdown.visibility = View.VISIBLE
                            speedup2.visibility = View.GONE
                            slowdown2.visibility = View.GONE
                        } else if(androidspeed < min - 2) {
                            speedcommand = "SPEED UP"
                            check.visibility = View.GONE
                            slowdown.visibility = View.GONE
                            speedup.visibility = View.VISIBLE
                            speedup2.visibility = View.GONE
                            slowdown2.visibility = View.GONE
                        } else if(androidspeed > max) {
                            speedcommand = "SLOWDOWN SLIGHTLY"
                            check.visibility = View.GONE
                            slowdown.visibility = View.GONE
                            speedup.visibility = View.GONE
                            speedup2.visibility = View.GONE
                            slowdown2.visibility = View.VISIBLE
                        } else if(androidspeed < min) {
                            speedcommand = "SPEED UP SLIGHTLY"
                            check.visibility = View.GONE
                            slowdown.visibility = View.GONE
                            speedup.visibility = View.GONE
                            speedup2.visibility = View.VISIBLE
                            slowdown2.visibility = View.GONE
                        } else {
                            speedcommand = "YOU ARE AT A GOOD SPEED"
                            speedup.visibility = View.GONE
                            slowdown.visibility = View.GONE
                            check.visibility = View.VISIBLE
                            speedup2.visibility = View.GONE
                            slowdown2.visibility = View.GONE
                        }
                    } else {
                        speedup.visibility = View.GONE
                        slowdown.visibility = View.GONE
                        speedup2.visibility = View.GONE
                        slowdown2.visibility = View.GONE
                        check.visibility = View.VISIBLE
                    }

                    if(trafficscreen == false) {
                        speedup.visibility = View.GONE
                        slowdown.visibility = View.GONE
                        check.visibility = View.GONE
                        speedup2.visibility = View.GONE
                        slowdown2.visibility = View.GONE
                    }

                    //Check Activity

                    activitytext = getActivity(androidspeed, maxspeed, on13th)

                    //Display text

                    if(appReady() == false) {
                        display = "App is currently initializing timestamps.."
                    } else if(on13th == true) {
                        display = "Your Speed: " + androidspeed + " meters per second"
                        display = display + "\nNext Stoplight: " + nextstop
                        display = display + "\nStoplight Color: " + initialcolor
                        display = display + "\nDistance to Next Stoplight: " + distance + " meters"
                        display = display + "\nTime Remaining Until Light Changes: " + timeremaining.toInt()
                        display = display + "\nMin Speed: " + min.toString() + "\nMax Speed: " + max.toString()
                        display = display + "\nSpeed Command: " + speedcommand
                        display = display + "\nCurrent Activity: " + activitytext
                        display = display + "\nLongitude: " + currentlocation.longitude.toString() + "\nLatitude: " + currentlocation.latitude.toString()
                    } else {
                        display = "You are currently not on 13th Avenue."
                    }

                    if(client.isConnected) {
                        display = display + "\n\nServer Status: Connected!"
                    } else {
                        display = display + "\n\nServer Status: Not Connected."
                    }

                    if(on13th == true) {
                        if(counter == 5) {
                            counter = 0
                            tts!!.speak(speedcommand, TextToSpeech.QUEUE_FLUSH, null)
                        } else {
                            counter++
                        }
                    }

                    text.setText(display)

                } else {
                    Log.d("TAGTAG", "NO INTERNET")
                }

            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0F, locationListener)
            Log.d("TAGTAG", "Permission Granted")
        } catch (ex: SecurityException) {
            Log.d("TAGTAG", "Location Permission Denied")
        }


        //MQTT STUFF

        client.setCallback(object : MqttCallbackExtended {

            override fun connectComplete(b: Boolean, s: String) {
                Log.w("MQTTTAG", "CONNECT COMPLETE")
            }

            override fun connectionLost(throwable: Throwable) {
                Log.w("MQTTTAG", "LOST IT")
            }

            @Throws(Exception::class)
            override fun messageArrived(topic: String, mqttMessage: MqttMessage) {
                var trafficlight = ""
                Log.w("MQTTTAG", "RECEIVED MESSAGE")
                Log.d("TIMETAG", System.currentTimeMillis().toString())

                parsePayload(mqttMessage.toString())

                if(active_phases.substring(1,2) == "2") {


                    if(topic == "TrafficData/4de40699-dbf3-4616-aed4-a77700e02c7e") {
                        trafficlight = "Hilyard"
                        timestamps[6] = timestamp
                    } else if (topic == "TrafficData/4bb0d4e7-32c5-4ba8-82b3-a77700df7ca0") {
                        trafficlight = "Patterson"
                        timestamps[5] = timestamp
                    } else if (topic == "TrafficData/bae4383a-913a-4fce-a9ae-a77700d4f7bc") {
                        trafficlight = "High"
                        timestamps[4] = timestamp
                    } else if (topic == "TrafficData/7b5379c8-8f7d-441e-bc09-a77700d5abb1") {
                        trafficlight = "Pearl"
                        timestamps[3] = timestamp
                    } else if (topic == "TrafficData/7a61c826-7122-40f2-81d0-a77700d65f1d") {
                        trafficlight = "Oak"
                        timestamps[2] = timestamp
                    } else if (topic == "TrafficData/6b6e36b0-6266-4237-b01b-a77700d74558") {
                        trafficlight = "Willamette"
                        timestamps[1] = timestamp
                    } else if (topic == "TrafficData/bdb0ab56-93e0-4c72-99ca-a77700d81bbf") {
                        trafficlight = "Olive"
                        timestamps[0] = timestamp
                    }
                }


                var time = Date()

                var seconds = time.seconds
                var minutes = time.minutes
                var hours = time.hours

                var text2 = "Server Message: " + mqttMessage.toString()

                text2 = text2 + "\n\nActive Phases: " + active_phases
                text2 = text2 + "\nCurrent Time: " + time
                text2 = text2 + "\nTraffic Light: " + trafficlight
                text2 = text2 + "\nSystem Time: " + System.currentTimeMillis().toString()
                text2 = text2 + "\nTime Stamp: " + timestamp.toString()


                Log.d("TIMETAG", "Active Phases: " + active_phases + "\nBecame Active Timestamp: " + timestampstring + "\nCurrent Time: " + hours + ":" + minutes + ":" + seconds + "\nTraffic Light: " + trafficlight)

                dataReceived.setText(text2)

                var message = MqttMessage()
                message.setPayload("MESSAGE WAS RECEIVED SUCCESSFULLY.".toByteArray())
                client.publish("Client Messages", message)
            }

            override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) {
                Log.d("MQTTTAG", "DELIVERY COMPLETE")
            }
        })


        val mqttConnectOptions = MqttConnectOptions()
        mqttConnectOptions.isAutomaticReconnect = true
        mqttConnectOptions.isCleanSession = false
        mqttConnectOptions.userName = username
        mqttConnectOptions.password = password.toCharArray()

        try {

            client.connect(mqttConnectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {

                    Log.d("MQTTTAG", "Connected")


                    if(client.isConnected) {


                        try {
                            client.subscribe("TrafficData/#", 1, null, object : IMqttActionListener {
                                override fun onSuccess(asyncActionToken: IMqttToken) {
                                    Log.w("MQTTTAG", "Subscribed!")
                                }

                                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                                    Log.w("MQTTTAG", "Subscribed fail!")
                                }
                            })

                        } catch (ex: MqttException) {
                            ex.printStackTrace()
                        }

                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.w("MQTTTAG", "Failed to connect to: " + serverUri + exception.toString())
                }
            })


        } catch (ex: MqttException) {
            Log.d("MQTTTAG2", "MQTT DID NOT CONNECT?")
            ex.printStackTrace()
        }
    }
}