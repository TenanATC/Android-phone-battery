# BluetoothDevice.getBatteryLevel is looked up reflectively on a framework
# class, which R8 does not touch; no app-specific rules are needed.
