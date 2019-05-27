import RPi.GPIO as GPIO
from time import sleep
import logging
import uuid

import Adafruit_BluefruitLE

# Define service and characteristic UUIDs used by the UART service.
UART_SERVICE_UUID = uuid.UUID('6f4cee11-d76c-49a5-b2d5-5b91e1db87b2')
DIRECTION_CHAR_UUID = uuid.UUID('7dbfd27a-b283-4ea3-a90d-75c58aea3511')

# Get the BLE provider for the Pi
ble = Adafruit_BluefruitLE.get_provider()

connected = True

Motor1_inp1 = 31    # Motor 1 Input Pin 1
Motor1_inp2 = 29    # Motor 1 Input Pin 2
Motor1 = 38    # Motor 1 Enable Pin
Motor2_inp1 = 11 # Motor 2 Input Pin 1
Motor2_inp2 = 13 # Motor 2 Input Pin 2
Motor2 = 36 #Motor 2 Enable Pin

# Used to convert received data into an integer
def bytes_to_int(bytes):
  return int(bytes.encode('hex'), 16)

# Callback for direction notifications
def received(data):
    device = None
    int_data = bytes_to_int(data)
    if int_data == 0:
        print("STOP")
        GPIO.output(Motor1_inp1, GPIO.LOW)
        GPIO.output(Motor1_inp2, GPIO.LOW)
        GPIO.output(Motor1, GPIO.LOW)
        GPIO.output(Motor2_inp1, GPIO.LOW)
        GPIO.output(Motor2_inp2, GPIO.LOW)
        GPIO.output(Motor2, GPIO.LOW)

    elif int_data == 1:
        print("FORWARD")
        GPIO.output(Motor1_inp1, GPIO.HIGH)
        GPIO.output(Motor1_inp2, GPIO.LOW)
        GPIO.output(Motor1, GPIO.HIGH)
        GPIO.output(Motor2_inp1, GPIO.HIGH)
        GPIO.output(Motor2_inp2, GPIO.LOW)
        GPIO.output(Motor2, GPIO.HIGH)

    elif int_data == 2:
        print("BACKWARDS")
        GPIO.output(Motor1_inp1, GPIO.LOW)
        GPIO.output(Motor1_inp2, GPIO.HIGH)
        GPIO.output(Motor1, GPIO.HIGH)
        GPIO.output(Motor2_inp1, GPIO.LOW)
        GPIO.output(Motor2_inp2, GPIO.HIGH)
        GPIO.output(Motor2, GPIO.HIGH)
    elif int_data == 3:
        print("LEFT")
        GPIO.output(Motor1_inp1, GPIO.HIGH)
        GPIO.output(Motor1_inp2, GPIO.LOW)
        GPIO.output(Motor1, GPIO.HIGH)
        GPIO.output(Motor2_inp1, GPIO.LOW)
        GPIO.output(Motor2_inp2, GPIO.HIGH)
        GPIO.output(Motor2, GPIO.HIGH)

    elif int_data == 4:
        print("RIGHT")
        GPIO.output(Motor1_inp1, GPIO.LOW)
        GPIO.output(Motor1_inp2, GPIO.HIGH)
        GPIO.output(Motor1, GPIO.HIGH)
        GPIO.output(Motor2_inp1, GPIO.HIGH)
        GPIO.output(Motor2_inp2, GPIO.LOW)
        GPIO.output(Motor2, GPIO.HIGH)

    elif int_data == 5:
        global connected
        connected = False

    else:
        print("Uh oh... this isn't supposed to happen")


def main():
    # Set Pi GPIO mode
    GPIO.setmode(GPIO.BOARD)

    # Setup GPIO pins
    GPIO.setup(Motor1_inp1,GPIO.OUT)
    GPIO.setup(Motor1_inp2,GPIO.OUT)
    GPIO.setup(Motor1,GPIO.OUT)
    GPIO.setup(Motor2_inp1,GPIO.OUT)
    GPIO.setup(Motor2_inp2,GPIO.OUT)
    GPIO.setup(Motor2,GPIO.OUT)

    # Clear cached data to avoid problems
    ble.clear_cached_data()

    # Get the first available BLE network adapter and make sure it's powered on
    adapter = ble.get_default_adapter()
    adapter.power_on()

    # Disconnect any currently connected devices
    print('Disconnecting any connected UART devices...')
    ble.disconnect_devices([UART_SERVICE_UUID])

    # Scan for UART devices
    print('Searching for UART device...')
    try:
        adapter.start_scan()
        # Search for the first UART device found (will time out after 60 seconds
        # but you can specify an optional timeout_sec parameter to change it)
        while True:
            device = ble.find_device(service_uuids=[UART_SERVICE_UUID], timeout_sec=10)
            if device is not None:
                print('Found Device!')
                break
            else:
                print('No device yet')

    finally:
        # Make sure scanning is stopped before exiting
        adapter.stop_scan()

    print('Connecting to device...')
    device.connect()

    try:
        print('Discovering services...')
        device.discover([UART_SERVICE_UUID], [DIRECTION_CHAR_UUID])

        # Find the UART service and its characteristics
        uart = device.find_service(UART_SERVICE_UUID)
        direction = uart.find_characteristic(DIRECTION_CHAR_UUID)

        # Turn on notification of the direction characteristic using the callback above
        print('Subscribing to TX characteristic changes...')
        direction.start_notify(received)

        # Accept data for 30 seconds
        print('Waiting 30 seconds to receive data from the device...')
        while connected:
            sleep(1)

    finally:
        # Make sure device is disconnected and stopped on exit
        GPIO.output(Motor1_inp1, GPIO.LOW)
        GPIO.output(Motor1_inp2, GPIO.LOW)
        GPIO.output(Motor1, GPIO.LOW)
        GPIO.output(Motor2_inp1, GPIO.LOW)
        GPIO.output(Motor2_inp2, GPIO.LOW)
        GPIO.output(Motor2, GPIO.LOW)
        device.disconnect()
        GPIO.cleanup()
        print('Finished!')

# Initialize the BLE system
ble.initialize()

# Start the mainloop to process BLE events
ble.run_mainloop_with(main)
