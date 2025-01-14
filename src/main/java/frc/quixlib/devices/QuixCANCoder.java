package frc.quixlib.devices;

import com.ctre.phoenix6.BaseStatusSignal;
import com.ctre.phoenix6.StatusCode;
import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.hardware.CANcoder;

import com.ctre.phoenix6.signals.SensorDirectionValue;
import com.ctre.phoenix6.sim.CANcoderSimState;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.quixlib.motorcontrol.MechanismRatio;
import frc.quixlib.phoenix.PhoenixUtil;

public class QuixCANCoder implements QuixAbsoluteEncoder {
  private static final double kCANTimeoutS = 0.1; // s
  private final CANDeviceID m_canID;
  private final CANcoder m_cancoder;
  private final CANcoderSimState m_simState;
  private final MechanismRatio m_ratio;

  private final QuixStatusSignal m_positionSignal;
  private final QuixStatusSignal m_absolutePositionSignal;
  private final QuixStatusSignal m_velocitySignal;

  public QuixCANCoder(final CANDeviceID canID, final MechanismRatio ratio) {
    m_canID = canID;
    m_cancoder = new CANcoder(canID.deviceNumber, canID.CANbusName);
    m_simState = m_cancoder.getSimState();
    m_ratio = ratio;

    m_positionSignal =
        new QuixStatusSignal<>(m_cancoder.getPosition(), this::fromNativeSensorPosition);
    m_absolutePositionSignal =
        new QuixStatusSignal<>(m_cancoder.getAbsolutePosition(), this::fromNativeSensorPosition);
    m_velocitySignal =
        new QuixStatusSignal<>(m_cancoder.getVelocity(), this::fromNativeSensorVelocity);

    // Clear reset flag.
    m_cancoder.hasResetOccurred();

    SmartDashboard.putBoolean("CANCoder Configuration " + m_canID.toString(), setConfiguration());
  }

  public boolean setConfiguration() {
    boolean allSuccess = true;

    // Set configuration.
    CANcoderConfiguration config = new CANcoderConfiguration();
    config.MagnetSensor.SensorDirection = SensorDirectionValue.CounterClockwise_Positive;
    config.MagnetSensor.MagnetOffset = 0.0;

    allSuccess &=
        PhoenixUtil.retryUntilSuccess(
            () -> m_cancoder.getConfigurator().apply(config, kCANTimeoutS),
            () -> {
              CANcoderConfiguration readConfig = new CANcoderConfiguration();
              m_cancoder.getConfigurator().refresh(readConfig, kCANTimeoutS);
              return PhoenixUtil.CANcoderConfigsEqual(config, readConfig);
            },
            "CANCoder " + m_canID + ": applyConfiguration");

    // Set update frequencies.
    allSuccess &=
        PhoenixUtil.retryUntilSuccess(
            () -> m_positionSignal.setUpdateFrequency(10.0, kCANTimeoutS),
            () -> m_positionSignal.getAppliedUpdateFrequency() == 10.0,
            "CANCoder " + m_canID + ": m_positionSignal.setUpdateFrequency()");
    allSuccess &=
        PhoenixUtil.retryUntilSuccess(
            () -> m_absolutePositionSignal.setUpdateFrequency(10.0, kCANTimeoutS),
            () -> m_absolutePositionSignal.getAppliedUpdateFrequency() == 10.0,
            "CANCoder " + m_canID + ": m_absolutePositionSignal.setUpdateFrequency()");
    allSuccess &=
        PhoenixUtil.retryUntilSuccess(
            () -> m_velocitySignal.setUpdateFrequency(10.0, kCANTimeoutS),
            () -> m_velocitySignal.getAppliedUpdateFrequency() == 10.0,
            "CANCoder " + m_canID + ": m_velocitySignal.setUpdateFrequency()");

    // Disable all signals that have not been explicitly defined.
    allSuccess &=
        PhoenixUtil.retryUntilSuccess(
            () -> m_cancoder.optimizeBusUtilization(kCANTimeoutS),
            "CANCoder " + m_canID + ": optimizeBusUtilization");

    // Block until we get valid signals.
    // allSuccess &=
    // PhoenixUtil.retryUntilSuccess(
    //   () -> waitForInputs(kCANTimeoutS), ": waitForInputs()");

    // Check if unlicensed.
    allSuccess &= !m_cancoder.getStickyFault_UnlicensedFeatureInUse().getValue();

    return allSuccess;
  }

  // Expose status signals for timesync
  public QuixStatusSignal positionSignal() {
    return m_positionSignal;
  }

  public QuixStatusSignal absolutePositionSignal() {
    return m_absolutePositionSignal;
  }

  public QuixStatusSignal velocitySignal() {
    return m_velocitySignal;
  }

  public void zero() {
    setPosition(0.0);
  }

  public void setPosition(final double pos) {
    m_cancoder.setPosition(toNativeSensorPosition(pos));
  }

  public double getPosition() {
    m_positionSignal.refresh();
    return (double) m_positionSignal.getRawValue();
  }

  public double getAbsPosition() {
    m_absolutePositionSignal.refresh();
    return (double) m_absolutePositionSignal.getRawValue();
  }

  public double getVelocity() {
    m_velocitySignal.refresh();
    return (double) m_velocitySignal.getRawValue();
  }

  private double toNativeSensorPosition(final double pos) {
    // Native units are in rotations.
    return m_ratio.mechanismPositionToSensorRadians(pos) / (2.0 * Math.PI);
  }

  private double fromNativeSensorPosition(final double pos) {
    return pos / toNativeSensorPosition(1.0);
  }

  private double toNativeSensorVelocity(final double vel) {
    return toNativeSensorPosition(vel);
  }

  private double fromNativeSensorVelocity(final double vel) {
    return vel / toNativeSensorVelocity(1.0);
  }

  public void setSimSensorVelocity(final double vel, final double dt) {
    final double rotationsPerSecond = toNativeSensorVelocity(vel);
    m_simState.setVelocity(rotationsPerSecond);
    m_simState.addPosition(rotationsPerSecond * dt);
  }

  
}
