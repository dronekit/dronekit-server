package com.geeksville.dapi

import org.json4s.JsonAST.JObject
import grizzled.slf4j.Logging
import scala.xml._
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.DefaultFormats
import org.json4s.Formats

case class ResultJSON(name: String, status: String, message: String, data: Option[String])

class AnalysisFactory(bytes: Array[Byte], isText: Boolean) extends Logging {

  protected implicit def jsonFormats: Formats = DefaultFormats

  def toJSON(): Option[JObject] = {

    val xml = XML.loadString(AnalysisFactory.testXML)

    val results = xml \\ "result"
    /*
    val resultsJson = results.map { r =>
      ResultJSON(r \ "name" toString, r \ "name" toString, r \ "name" toString, r \ "name" map (_.toString) headOption): JObject
    }
    println(s"Returning json: $resultsJson")
    Some(JObject(resultsJson: _*))
    * 
    */

    Some(None)
  }
}

object AnalysisFactory {
  val testXML = """
	<?xml version="1.0" encoding="UTF-8"?>
	<loganalysis>
	<header>
	  <logfile>/home/kevinh/tmp/test.log</logfile>
	  <sizekb>2775.068359375</sizekb>
	  <sizelines>41568</sizelines>
	  <duration>0:03:03</duration>
	  <vehicletype>ArduCopter</vehicletype>
	  <firmwareversion>V3.1.5</firmwareversion>
	  <firmwarehash>ee63c88b</firmwarehash>
	  <hardwaretype>PX4</hardwaretype>
	  <freemem>65535</freemem>
	  <skippedlines>0</skippedlines>
	</header>
	<params>
	  <param name="RC7_REV" value="1.0" />
	  <param name="MNT_MODE" value="3.0" />
	  <param name="LOITER_LON_P" value="1.0" />
	  <param name="FLTMODE1" value="0.0" />
	  <param name="FLTMODE3" value="3.0" />
	  <param name="FLTMODE2" value="5.0" />
	  <param name="FLTMODE5" value="2.0" />
	  <param name="FLTMODE4" value="0.0" />
	  <param name="FLTMODE6" value="6.0" />
	  <param name="SYSID_SW_TYPE" value="10.0" />
	  <param name="LOITER_LON_D" value="0.0" />
	  <param name="RC5_REV" value="1.0" />
	  <param name="THR_RATE_IMAX" value="300.0" />
	  <param name="MNT_RC_IN_PAN" value="0.0" />
	  <param name="MNT_RETRACT_X" value="0.0" />
	  <param name="RC2_MIN" value="992.0" />
	  <param name="RELAY_PIN" value="54.0" />
	  <param name="LOITER_LON_I" value="0.5" />
	  <param name="COMPASS_OFS2_X" value="0.0" />
	  <param name="STB_RLL_I" value="0.0" />
	  <param name="AHRS_GPS_GAIN" value="1.0" />
	  <param name="MNT_CONTROL_Y" value="0.0" />
	  <param name="MNT_CONTROL_X" value="0.0" />
	  <param name="FRAME" value="2.0" />
	  <param name="COMPASS_OFS_X" value="-69.04713" />
	  <param name="OF_PIT_IMAX" value="100.0" />
	  <param name="ANGLE_RATE_MAX" value="18000.0" />
	  <param name="SIMPLE" value="0.0" />
	  <param name="RATE_RLL_I" value="0.1425" />
	  <param name="RC2_MAX" value="2014.0" />
	  <param name="MNT_JSTICK_SPD" value="0.0" />
	  <param name="RC8_FUNCTION" value="0.0" />
	  <param name="COMPASS_MOT2_X" value="0.0" />
	  <param name="RC12_REV" value="1.0" />
	  <param name="OF_RLL_P" value="2.5" />
	  <param name="STB_RLL_P" value="10.134" />
	  <param name="COMPASS_MOT2_Y" value="0.0" />
	  <param name="INS_ACC2OFFS_Z" value="1.287664" />
	  <param name="STB_YAW_P" value="6.0" />
	  <param name="SR0_RAW_SENS" value="0.0" />
	  <param name="TUNE_HIGH" value="1000.0" />
	  <param name="SR2_PARAMS" value="0.0" />
	  <param name="RATE_YAW_I" value="0.02" />
	  <param name="MAG_ENABLE" value="1.0" />
	  <param name="MNT_RETRACT_Y" value="0.0" />
	  <param name="SR1_EXTRA3" value="2.0" />
	  <param name="RATE_YAW_IMAX" value="800.0" />
	  <param name="WPNAV_SPEED_DN" value="150.0" />
	  <param name="MOT_TCRV_MAXPCT" value="80.0" />
	  <param name="WP_YAW_BEHAVIOR" value="1.0" />
	  <param name="RC11_REV" value="1.0" />
	  <param name="SYSID_THISMAV" value="1.0" />
	  <param name="SR0_EXTRA1" value="0.0" />
	  <param name="SR0_EXTRA2" value="0.0" />
	  <param name="ACRO_BAL_PITCH" value="1.0" />
	  <param name="STB_YAW_I" value="0.0" />
	  <param name="INS_ACCSCAL_Z" value="0.988255" />
	  <param name="RC12_FUNCTION" value="0.0" />
	  <param name="INS_ACCSCAL_X" value="0.995222" />
	  <param name="FS_GCS_ENABLE" value="0.0" />
	  <param name="MNT_RC_IN_ROLL" value="0.0" />
	  <param name="INAV_TC_Z" value="5.0" />
	  <param name="RATE_PIT_IMAX" value="500.0" />
	  <param name="HLD_LON_IMAX" value="0.0" />
	  <param name="THR_RATE_I" value="0.0" />
	  <param name="RC12_DZ" value="0.0" />
	  <param name="INS_GYR2OFFS_X" value="0.013739" />
	  <param name="SR1_POSITION" value="3.0" />
	  <param name="STB_PIT_IMAX" value="0.0" />
	  <param name="AHRS_TRIM_Z" value="0.0" />
	  <param name="RC2_REV" value="1.0" />
	  <param name="INS_MPU6K_FILTER" value="0.0" />
	  <param name="SR2_RAW_SENS" value="0.0" />
	  <param name="THR_MIN" value="130.0" />
	  <param name="RC11_DZ" value="0.0" />
	  <param name="THR_MAX" value="1000.0" />
	  <param name="COMPASS_USE" value="1.0" />
	  <param name="MNT_NEUTRAL_Z" value="0.0" />
	  <param name="THR_MID" value="580.0" />
	  <param name="MNT_NEUTRAL_X" value="0.0" />
	  <param name="RELAY_PIN3" value="-1.0" />
	  <param name="RELAY_PIN2" value="-1.0" />
	  <param name="MNT_STAB_PAN" value="0.0" />
	  <param name="FS_BATT_ENABLE" value="1.0" />
	  <param name="RC5_TRIM" value="1146.0" />
	  <param name="OF_PIT_D" value="0.12" />
	  <param name="SR0_PARAMS" value="0.0" />
	  <param name="ACRO_RP_P" value="4.5" />
	  <param name="COMPASS_ORIENT" value="0.0" />
	  <param name="WPNAV_ACCEL" value="100.0" />
	  <param name="THR_ACCEL_IMAX" value="500.0" />
	  <param name="RC12_MIN" value="1100.0" />
	  <param name="WP_TOTAL" value="6.0" />
	  <param name="RC8_MAX" value="1505.0" />
	  <param name="SR2_EXT_STAT" value="0.0" />
	  <param name="OF_PIT_P" value="2.5" />
	  <param name="RC9_TRIM" value="1500.0" />
	  <param name="RTL_ALT_FINAL" value="0.0" />
	  <param name="RCMAP_THROTTLE" value="3.0" />
	  <param name="SR0_EXTRA3" value="0.0" />
	  <param name="LOITER_LAT_I" value="0.5" />
	  <param name="RC6_DZ" value="0.0" />
	  <param name="CAM_TRIGG_DIST" value="0.0" />
	  <param name="RC4_TRIM" value="1507.0" />
	  <param name="RCMAP_YAW" value="4.0" />
	  <param name="RATE_RLL_P" value="0.1425" />
	  <param name="INS_ACC2SCAL_Y" value="1.034238" />
	  <param name="GPSGLITCH_ACCEL" value="1000.0" />
	  <param name="LOITER_LAT_D" value="0.0" />
	  <param name="STB_PIT_P" value="7.400683" />
	  <param name="OF_PIT_I" value="0.5" />
	  <param name="RC_FEEL_RP" value="75.0" />
	  <param name="AHRS_TRIM_X" value="-0.005545" />
	  <param name="RC3_REV" value="1.0" />
	  <param name="STB_PIT_I" value="0.0" />
	  <param name="FS_THR_ENABLE" value="2.0" />
	  <param name="LOITER_LAT_P" value="1.0" />
	  <param name="RC12_MAX" value="1900.0" />
	  <param name="FENCE_ACTION" value="1.0" />
	  <param name="RATE_RLL_D" value="0.00875" />
	  <param name="RC5_MIN" value="1146.0" />
	  <param name="LAND_SPEED" value="50.0" />
	  <param name="RC9_MAX" value="1520.0" />
	  <param name="STB_RLL_IMAX" value="0.0" />
	  <param name="AHRS_GPS_DELAY" value="1.0" />
	  <param name="RC4_DZ" value="40.0" />
	  <param name="AHRS_YAW_P" value="0.1" />
	  <param name="RC9_MIN" value="1000.0" />
	  <param name="MOT_TCRV_ENABLE" value="1.0" />
	  <param name="CAM_TRIGG_TYPE" value="0.0" />
	  <param name="SR2_EXTRA3" value="0.0" />
	  <param name="STB_YAW_IMAX" value="0.0" />
	  <param name="RC4_MAX" value="2009.0" />
	  <param name="LOITER_LAT_IMAX" value="400.0" />
	  <param name="CH7_OPT" value="0.0" />
	  <param name="RC11_FUNCTION" value="0.0" />
	  <param name="SR0_EXT_STAT" value="0.0" />
	  <param name="SONAR_TYPE" value="0.0" />
	  <param name="RC3_MAX" value="2009.0" />
	  <param name="RATE_YAW_D" value="0.0" />
	  <param name="FENCE_ALT_MAX" value="100.0" />
	  <param name="COMPASS_MOT_Y" value="0.0" />
	  <param name="INS_PRODUCT_ID" value="5.0" />
	  <param name="FENCE_ENABLE" value="0.0" />
	  <param name="RC10_DZ" value="0.0" />
	  <param name="PILOT_VELZ_MAX" value="250.0" />
	  <param name="BATT_CAPACITY" value="3300.0" />
	  <param name="FS_THR_VALUE" value="975.0" />
	  <param name="RC4_MIN" value="996.0" />
	  <param name="MNT_ANGMAX_TIL" value="0.0" />
	  <param name="RTL_LOIT_TIME" value="5000.0" />
	  <param name="ARMING_CHECK" value="0.0" />
	  <param name="THR_RATE_P" value="5.0" />
	  <param name="SERIAL2_BAUD" value="57.0" />
	  <param name="COMPASS_OFS2_Z" value="0.0" />
	  <param name="RC6_MIN" value="991.0" />
	  <param name="SR0_RAW_CTRL" value="0.0" />
	  <param name="RC6_MAX" value="2016.0" />
	  <param name="SR1_RAW_SENS" value="2.0" />
	  <param name="SR1_RC_CHAN" value="2.0" />
	  <param name="RC5_MAX" value="1863.0" />
	  <param name="LOITER_LON_IMAX" value="400.0" />
	  <param name="AHRS_ORIENTATION" value="0.0" />
	  <param name="MNT_STAB_TILT" value="0.0" />
	  <param name="MNT_CONTROL_Z" value="0.0" />
	  <param name="COMPASS_OFS_Z" value="54.1102" />
	  <param name="COMPASS_OFS_Y" value="33.92496" />
	  <param name="FS_BATT_MAH" value="5.0" />
	  <param name="THR_ALT_I" value="0.0" />
	  <param name="SR1_EXT_STAT" value="2.0" />
	  <param name="ANGLE_MAX" value="3000.0" />
	  <param name="RC10_TRIM" value="1500.0" />
	  <param name="GPSGLITCH_ENABLE" value="1.0" />
	  <param name="RC11_MIN" value="1100.0" />
	  <param name="GND_ALT_OFFSET" value="0.0" />
	  <param name="RSSI_RANGE" value="5.0" />
	  <param name="FS_GPS_ENABLE" value="2.0" />
	  <param name="SERIAL1_BAUD" value="57.0" />
	  <param name="INS_ACC2OFFS_Y" value="1.124601" />
	  <param name="SR0_POSITION" value="0.0" />
	  <param name="RC3_TRIM" value="1487.0" />
	  <param name="RC6_FUNCTION" value="0.0" />
	  <param name="MOT_TCRV_MIDPCT" value="52.0" />
	  <param name="SR1_PARAMS" value="10.0" />
	  <param name="TRIM_THROTTLE" value="708.0" />
	  <param name="MNT_STAB_ROLL" value="0.0" />
	  <param name="INAV_TC_XY" value="2.5" />
	  <param name="BATT_AMP_PERVOLT" value="17.0" />
	  <param name="BATT_VOLT_MULT" value="12.02" />
	  <param name="RELAY_PIN4" value="-1.0" />
	  <param name="RC1_DZ" value="30.0" />
	  <param name="GPSGLITCH_RADIUS" value="200.0" />
	  <param name="MNT_RETRACT_Z" value="0.0" />
	  <param name="LOG_BITMASK" value="32767.0" />
	  <param name="TUNE_LOW" value="0.0" />
	  <param name="SR2_RC_CHAN" value="0.0" />
	  <param name="CIRCLE_RATE" value="20.0" />
	  <param name="MOT_SPIN_ARMED" value="90.0" />
	  <param name="CAM_DURATION" value="10.0" />
	  <param name="MNT_NEUTRAL_Y" value="0.0" />
	  <param name="RC9_REV" value="1.0" />
	  <param name="INS_ACCOFFS_X" value="-0.099559" />
	  <param name="THR_RATE_D" value="0.0" />
	  <param name="INS_ACCOFFS_Z" value="0.632074" />
	  <param name="SR2_EXTRA1" value="0.0" />
	  <param name="RC4_REV" value="1.0" />
	  <param name="CIRCLE_RADIUS" value="10.0" />
	  <param name="HLD_LAT_IMAX" value="0.0" />
	  <param name="HLD_LAT_P" value="1.0" />
	  <param name="BRD_PWM_COUNT" value="4.0" />
	  <param name="AHRS_GPS_MINSATS" value="6.0" />
	  <param name="GPS_HDOP_GOOD" value="200.0" />
	  <param name="FLOW_ENABLE" value="0.0" />
	  <param name="RC8_REV" value="1.0" />
	  <param name="SONAR_GAIN" value="0.8" />
	  <param name="RC2_TRIM" value="1503.0" />
	  <param name="WP_INDEX" value="1.0" />
	  <param name="RC1_REV" value="1.0" />
	  <param name="RC12_TRIM" value="1500.0" />
	  <param name="RC7_DZ" value="0.0" />
	  <param name="RCMAP_PITCH" value="2.0" />
	  <param name="AHRS_GPS_USE" value="1.0" />
	  <param name="MNT_ANGMIN_PAN" value="-4500.0" />
	  <param name="ACRO_YAW_P" value="4.5" />
	  <param name="COMPASS_LEARN" value="0.0" />
	  <param name="ACRO_TRAINER" value="2.0" />
	  <param name="CAM_SERVO_OFF" value="1100.0" />
	  <param name="RC5_DZ" value="0.0" />
	  <param name="SCHED_DEBUG" value="0.0" />
	  <param name="RC11_MAX" value="1900.0" />
	  <param name="AHRS_WIND_MAX" value="0.0" />
	  <param name="MNT_ANGMIN_TIL" value="-9000.0" />
	  <param name="MNT_ANGMAX_PAN" value="4500.0" />
	  <param name="MNT_ANGMAX_ROL" value="4500.0" />
	  <param name="RC_SPEED" value="490.0" />
	  <param name="MNT_ANGMIN_ROL" value="-4500.0" />
	  <param name="SUPER_SIMPLE" value="0.0" />
	  <param name="RC11_TRIM" value="1500.0" />
	  <param name="SR1_RAW_CTRL" value="0.0" />
	  <param name="COMPASS_MOTCT" value="0.0" />
	  <param name="RC10_MIN" value="1100.0" />
	  <param name="RCMAP_ROLL" value="1.0" />
	  <param name="WPNAV_RADIUS" value="200.0" />
	  <param name="SONAR_ENABLE" value="0.0" />
	  <param name="MNT_RC_IN_TILT" value="6.0" />
	  <param name="INS_ACCSCAL_Y" value="0.998081" />
	  <param name="INS_ACC2OFFS_X" value="1.126673" />
	  <param name="INS_ACCOFFS_Y" value="-0.124072" />
	  <param name="SR2_EXTRA2" value="0.0" />
	  <param name="SYSID_SW_MREV" value="120.0" />
	  <param name="WPNAV_LOIT_SPEED" value="600.0" />
	  <param name="RATE_RLL_IMAX" value="500.0" />
	  <param name="COMPASS_OFS2_Y" value="0.0" />
	  <param name="CH8_OPT" value="0.0" />
	  <param name="RTL_ALT" value="2000.0" />
	  <param name="RC9_FUNCTION" value="7.0" />
	  <param name="RC1_MIN" value="995.0" />
	  <param name="RSSI_PIN" value="-1.0" />
	  <param name="COMPASS_MOT2_Z" value="0.0" />
	  <param name="GND_ABS_PRESS" value="96041.51" />
	  <param name="THR_ALT_P" value="1.0" />
	  <param name="SR2_RAW_CTRL" value="0.0" />
	  <param name="RC1_MAX" value="2016.0" />
	  <param name="FENCE_TYPE" value="1.0" />
	  <param name="RC5_FUNCTION" value="0.0" />
	  <param name="OF_RLL_D" value="0.12" />
	  <param name="HLD_LON_P" value="1.0" />
	  <param name="WPNAV_SPEED" value="1200.0" />
	  <param name="RC7_MAX" value="2017.0" />
	  <param name="CAM_SERVO_ON" value="1300.0" />
	  <param name="RATE_PIT_I" value="0.094999" />
	  <param name="AHRS_RP_P" value="0.1" />
	  <param name="RC7_MIN" value="992.0" />
	  <param name="AHRS_COMP_BETA" value="0.1" />
	  <param name="OF_RLL_I" value="0.5" />
	  <param name="COMPASS_DEC" value="0.176979" />
	  <param name="FENCE_MARGIN" value="2.0" />
	  <param name="SR2_POSITION" value="0.0" />
	  <param name="RC3_MIN" value="992.0" />
	  <param name="RC2_DZ" value="30.0" />
	  <param name="SR1_EXTRA2" value="10.0" />
	  <param name="SR1_EXTRA1" value="10.0" />
	  <param name="HLD_LON_I" value="0.0" />
	  <param name="ACRO_BAL_ROLL" value="1.0" />
	  <param name="COMPASS_AUTODEC" value="1.0" />
	  <param name="AHRS_TRIM_Y" value="0.000488" />
	  <param name="OF_RLL_IMAX" value="100.0" />
	  <param name="BATT_AMP_OFFSET" value="0.0" />
	  <param name="RC10_MAX" value="1900.0" />
	  <param name="INS_ACC2SCAL_Z" value="1.029984" />
	  <param name="INS_ACC2SCAL_X" value="0.986631" />
	  <param name="RATE_PIT_P" value="0.094999" />
	  <param name="GND_TEMP" value="29.05656" />
	  <param name="RC7_TRIM" value="2016.0" />
	  <param name="RC10_REV" value="1.0" />
	  <param name="RATE_YAW_P" value="0.2" />
	  <param name="RC9_DZ" value="0.0" />
	  <param name="RATE_PIT_D" value="0.00775" />
	  <param name="ESC" value="0.0" />
	  <param name="SR0_RC_CHAN" value="0.0" />
	  <param name="RC8_MIN" value="1503.0" />
	  <param name="THR_ALT_IMAX" value="300.0" />
	  <param name="SYSID_MYGCS" value="255.0" />
	  <param name="BATT_MONITOR" value="4.0" />
	  <param name="INS_GYROFFS_Y" value="0.008694" />
	  <param name="TUNE" value="0.0" />
	  <param name="FS_BATT_VOLTAGE" value="10.3" />
	  <param name="RC8_TRIM" value="1504.0" />
	  <param name="RC3_DZ" value="30.0" />
	  <param name="INS_GYR2OFFS_Z" value="-0.002642" />
	  <param name="THR_ACCEL_D" value="0.0" />
	  <param name="TELEM_DELAY" value="0.0" />
	  <param name="INS_GYR2OFFS_Y" value="0.018731" />
	  <param name="THR_ACCEL_I" value="1.5" />
	  <param name="COMPASS_MOT_X" value="0.0" />
	  <param name="COMPASS_MOT_Z" value="0.0" />
	  <param name="RC10_FUNCTION" value="8.0" />
	  <param name="INS_GYROFFS_X" value="-0.004545" />
	  <param name="INS_GYROFFS_Z" value="0.010592" />
	  <param name="RC6_TRIM" value="992.0" />
	  <param name="THR_ACCEL_P" value="0.75" />
	  <param name="RC8_DZ" value="0.0" />
	  <param name="HLD_LAT_I" value="0.0" />
	  <param name="RC7_FUNCTION" value="0.0" />
	  <param name="RC6_REV" value="1.0" />
	  <param name="COMPASS_EXTERNAL" value="1.0" />
	  <param name="FENCE_RADIUS" value="300.0" />
	  <param name="BATT_VOLT_PIN" value="2.0" />
	  <param name="BATT_CURR_PIN" value="3.0" />
	  <param name="WPNAV_SPEED_UP" value="250.0" />
	  <param name="RC1_TRIM" value="1503.0" />
	</params>
	<results>
	  <result>
	    <name>Dupe Log Data</name>
	    <status>GOOD</status>
	    <message></message>
	  </result>
	  <result>
	    <name>VCC</name>
	    <status>GOOD</status>
	    <message></message>
	  </result>
	  <result>
	    <name>Event/Failsafe</name>
	    <status>FAIL</status>
	    <message>ERR found: FS_THR </message>
	    <data>(test data will be embeded here at some point)</data>
	  </result>
	  <result>
	    <name>Brownout</name>
	    <status>FAIL</status>
	    <message>Truncated Log? Ends while armed at altitude 6.66m</message>
	    <data>(test data will be embeded here at some point)</data>
	  </result>
	  <result>
	    <name>Parameters</name>
	    <status>GOOD</status>
	    <message></message>
	  </result>
	  <result>
	    <name>GPS</name>
	    <status>GOOD</status>
	    <message></message>
	  </result>
	  <result>
	    <name>PM</name>
	    <status>GOOD</status>
	    <message></message>
	  </result>
	  <result>
	    <name>Pitch/Roll</name>
	    <status>GOOD</status>
	    <message></message>
	  </result>
	  <result>
	    <name>Thrust</name>
	    <status>FAIL</status>
	    <message>Avg climb rate -4.16 cm/s for throttle avg 785</message>
	    <data>(test data will be embeded here at some point)</data>
	  </result>
	  <result>
	    <name>Compass</name>
	    <status>GOOD</status>
	    <message>mag_field interference within limits (22.87%)
	</message>
	  </result>
	  <result>
	    <name>Vibration</name>
	    <status>UNKNOWN</status>
	    <message>No stable LOITER log data found</message>
	  </result>
	  <result>
	    <name>Empty</name>
	    <status>GOOD</status>
	    <message></message>
	  </result>
	</results>
	</loganalysis>
"""

}