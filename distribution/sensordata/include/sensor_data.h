#ifndef __SENSOR_DATA_H__
#define __SENSOR_DATA_H__

#ifdef _WIN32

#ifdef __cplusplus
#define SENSOR_DATA_EXPORT extern "C" __declspec (dllexport)
#endif

#else //Android/Linux
#define SENSOR_DATA_EXPORT extern "C"
#endif

enum SensorData_CODE
{
	SUCCESS = 0,
	FAIL = 1,
	SENSOR_ID_ERROR = 2,
	PROP_ERROR = 3,
	VALUE_ERROR,
	NOT_SUPPORT
};

/********************************************************************************************************
@Function: imu data callback function defination
@Description: when a imu data comes, this function will be invoked if registered
@hardware_sync_imu_index: cameras hardware sync frame index
if there several cameras/sensors should be synced with IMU, store synced index in the array as sensor list order.
for example, when a camera frame generated, the frame index increase to 3, then after the camera hardware sync
info received by IMU, the later IMU data's hardware_sync_imu_index should be 3. just like:
fisheye hardware_frame_index:  0                 1                 2                  3
TOF hardware_frame_index:      6                          7                         8
hardware_sync_imu_index[0]:    0 0 0 0 0 0 0 0 0 1 1 1 1 1 1 1 1 1 2 2 2 2 2 2 2 2 2 2 3 3 3 3 3
hardware_sync_imu_index[1]:    6 6 6 6 6 6 6 6 6 6 6 6 6 6 7 7 7 7 7 7 7 7 7 7 7 7 7 8 8 8 8 8 8
@synced_sensor_num:	the number of synced sensors(currently only support one camera sync)
@acc:				imu data, the 3 axis data of acceleration, unit: m^2/s
@gyro:				imu data, the 3 axis data of gyroscope, unit: rad/s
@mag:				imu data, the 3 axis data of magnet, unit: ut
@temperature:		imu temperature, unit: ¡æ
@time_stamp:		imu data timestamp,  unit: ns
@tag:				some auxiliary infomation of data
@Note:
IMU frequency is very high, please don't do time consuming
(>1/FPS seconds) operation in this callback function,
because it is invoked in very high IMU frequency.
********************************************************************************************************/
typedef SensorData_CODE (imu_data_callback)(int hardware_sync_imu_index[], int synced_sensor_num, float acc[3], float gyro[3], float mag[3], double temperature, double time_stamp, const char  tag[10]);


/********************************************************************************************************
@Function: camera data callback function defination
@Description: when a camera data comes, this function will be invoked if registered
@hardware_frame_index: cameras hardware invoked frame index, be accumulated with camera hardware exposure
times(could be not continous because of ISP processing or frame missing)
@width:			number of columns of camera image
@height:		number of rows of camera image
@image_buffer:	image data buffer.
different image buffer have different pixel format, the buffer memory pointer is casted to char* to pass
to the interface, and cast back to original format by user. some format reference:
fisheye:		unsigned char
depth:			short
RGB:			YUV(TBD)
@time_stamp:	camera data timestamp,  unit: ns
@tag:			some auxiliary infomation of data
@Note:
camera frequency is high, please don't do time consuming
(>1/FPS seconds) operation in this callback function,
because it is invoked in high frequency.
********************************************************************************************************/
typedef SensorData_CODE (camera_data_callback)(int hardware_frame_index, int width, int height, char* image_buffer, double time_stamp, const char  tag[10]);

/********************************************************************************************************
@Function: depth camera point cloud data callback function defination
@Description: when a depth camera point cloud data comes, this function will be invoked if registered
@hardware_frame_index: cameras hardware invoked frame index, be accumulated with camera hardware exposure
times(could be not continous because of ISP processing or frame missing)
@pointcloud_len:		number of point clouds
@pointcloud_buffer:		buffer of point cloud
@time_stamp:			point cloud data timestamp,  unit: ns
@tag:					some auxiliary infomation of data

@Note:
camera frequency is high, please don't do time consuming
(>1/FPS seconds) operation in this callback function,
because it is invoked in high frequency.
********************************************************************************************************/
typedef SensorData_CODE (pointcloud_data_callback)(int hardware_frame_index, int pointcloud_len, float* pointcloud_buffer, double time_stamp, const char  tag[10]);





/********************************************************************************************************
@Function: get_sensor_list
@Description: the function to get list of sensor id of device
@camera_list:		list of sensor id, each sensor's id descripted by a string(char*),for example:
"camera:fisheye"
"camera:rgb"
"camera:tof"
"imu"
@sensor_num:		the number of sensors
@Note:
********************************************************************************************************/
SENSOR_DATA_EXPORT SensorData_CODE get_sensor_list(char* sensor_ids[], int &sensor_num);


/********************************************************************************************************
@Function: set_sensor_param
@Description:	the function to set some parameter of sensors
@sensor_id:		sensor id of imu sensor, which get from get_sensor_list
@prop:			the property to set
@value:			the set value of properties
@Note:
define:
for camera properties:
"FPS", value: int
"exposure_mode", value: int, 0(fixed), 1(auto)
"exposure_time", value: float
"max_exposure_time", value: float
"focus_mode", value: int, 0(fixed), 1(auto)

for imu properties:
"FPS", value: int
"filter_mode", value: int

for example:
int fps = 50;
set_sensor_param("camera:fisheye", "FPS", &fps);
********************************************************************************************************/
SENSOR_DATA_EXPORT SensorData_CODE set_sensor_param(char* sensor_id, const char* prop, void* value);


/********************************************************************************************************
@Function: get_sensor_param
@Description:	the function to get some parameter of sensors
@sensor_id:		sensor id of sensor, which get from get_sensor_list
@prop:			the property to get
@value:			the gotten value of property
@Note:
for camera properties:
"FPS", value: int
"exposure_mode", value: int, 0(fixed), 1(auto)
"exposure_time", value: float
"max_exposure_time", value: float
"focus_mode", value: int, 0(fixed), 1(auto)

for imu properties:
"FPS", value: int
"filter_mode", value: int

for example:
int fps = 0;
get_sensor_param("camera:fisheye", "FPS", &fps);
********************************************************************************************************/
SENSOR_DATA_EXPORT SensorData_CODE get_sensor_param(char* sensor_id, const char* prop, void* value);



/********************************************************************************************************
@Function: register_imu_callback/register_camera_callback/register_pointcloud_callback
@Description: the interface to register data callback function
@sensor_id:		sensor id of sensor, which get from get_sensor_list
@cb:			callback function pointer
@param:			TBD, for some extention, can pass NULL
@Note:
callback functions should be before sensor started.
********************************************************************************************************/
SENSOR_DATA_EXPORT SensorData_CODE register_imu_callback(char* sensor_id, imu_data_callback* cb, void* param);
SENSOR_DATA_EXPORT SensorData_CODE register_camera_callback(char* sensor_id, camera_data_callback* cb, void* param);
SENSOR_DATA_EXPORT SensorData_CODE register_pointcloud_callback(char* sensor_id, pointcloud_data_callback* cb, void* param);
SENSOR_DATA_EXPORT SensorData_CODE register_connect_callback(void (*cb_pullout)(void), void (*cb_plugin)(void));


/********************************************************************************************************
@Function: start_sensor/stop_sensor
@Description: the interface to start/stop sensor data
after start/stop sensor, the data callback will  start/stop to invoke.
@sensor_id:		sensor id of imu sensor, which get from get_sensor_list
@Note:
********************************************************************************************************/
SENSOR_DATA_EXPORT SensorData_CODE start_sensor(char* sensor_id);
SENSOR_DATA_EXPORT SensorData_CODE stop_sensor(char* sensor_id);

SENSOR_DATA_EXPORT SensorData_CODE initial_sensor();
SENSOR_DATA_EXPORT SensorData_CODE release_sensor();

SensorData_CODE setUsbParams(int vid, int pid, int fd, int busnum, int devaddr, const char *usbfs);
SensorData_CODE setStUfd(int vid, int pid, int fd, int busnum, int devaddr, const char *usbfs);

#endif
