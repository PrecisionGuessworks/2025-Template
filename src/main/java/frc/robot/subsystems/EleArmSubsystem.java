// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.system.plant.LinearSystemId;
import edu.wpi.first.math.trajectory.TrapezoidProfile.State;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.simulation.FlywheelSim;
import edu.wpi.first.wpilibj.simulation.SingleJointedArmSim;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.quixlib.math.MathUtils;
import frc.quixlib.motorcontrol.QuixTalonFX;
import frc.quixlib.planning.QuixTrapezoidProfile;
import frc.quixlib.viz.Link2d;
import frc.robot.Constants;

public class EleArmSubsystem extends SubsystemBase {
  public final DigitalInput m_beamBreak = new DigitalInput(Constants.EleArm.beamBreakPort);

  // This motor controls the upper launch wheels
  private final QuixTalonFX m_upperLaunchMotor =
      new QuixTalonFX(
          Constants.EleArm.upperMotorID,
          Constants.EleArm.upperMotorRatio,
          QuixTalonFX.makeDefaultConfig()
              .setInverted(Constants.EleArm.upperMotorInvert)
              .setPIDConfig(
                  Constants.EleArm.EleArmVelocityPIDSlot,
                  Constants.EleArm.EleArmVelocityPIDConfig)
              .setSupplyCurrentLimit(40.0)
              .setStatorCurrentLimit(120.0));

  // This motor controls the lower launch wheels
  private final QuixTalonFX m_lowerLaunchMotor =
      new QuixTalonFX(
          Constants.EleArm.lowerMotorID,
          Constants.EleArm.lowerMotorRatio,
          QuixTalonFX.makeDefaultConfig()
              .setInverted(Constants.EleArm.lowerMotorInvert)
              .setPIDConfig(
                  Constants.EleArm.EleArmVelocityPIDSlot,
                  Constants.EleArm.EleArmVelocityPIDConfig)
              .setSupplyCurrentLimit(40.0)
              .setStatorCurrentLimit(120.0));

  // This motor controls the feed rollers
  private final QuixTalonFX m_feedRollerMotor =
      new QuixTalonFX(
          Constants.EleArm.feedMotorID,
          Constants.EleArm.feedMotorRatio,
          QuixTalonFX.makeDefaultConfig()
              .setInverted(Constants.EleArm.feedMotorInvert)
              .setBrakeMode()
              .setPIDConfig(
                  Constants.EleArm.feedVelocityPIDSlot, Constants.EleArm.feedVelocityPIDConfig)
              .setPIDConfig(
                  Constants.EleArm.feedPositionPIDSlot, Constants.EleArm.feedPositionPIDConfig)
              .setSupplyCurrentLimit(40.0)
              .setStatorCurrentLimit(60.0));

  // This motor controls the redirect roller
  private final QuixTalonFX m_redirectRollerMotor =
      new QuixTalonFX(
          Constants.EleArm.redirectMotorID,
          Constants.EleArm.redirectMotorRatio,
          QuixTalonFX.makeDefaultConfig()
              .setInverted(Constants.EleArm.redirectMotorInvert)
              .setBrakeMode()
              .setPIDConfig(
                  Constants.EleArm.redirectVelocityPIDSlot,
                  Constants.EleArm.redirectVelocityPIDConfig)
              .setPIDConfig(
                  Constants.EleArm.redirectPositionPIDSlot,
                  Constants.EleArm.redirectPositionPIDConfig)
              .setSupplyCurrentLimit(40.0)
              .setStatorCurrentLimit(60.0));

  // This motor controls the angle of the arm (EleArm)
  private final QuixTalonFX m_armAngleMotor =
      new QuixTalonFX(
          Constants.EleArm.armMotorID,
          Constants.EleArm.armMotorRatio,
          QuixTalonFX.makeDefaultConfig()
              .setInverted(Constants.EleArm.armMotorInvert)
              .setBrakeMode()
              .setPIDConfig(
                  Constants.EleArm.armPositionPIDSlot, Constants.EleArm.armPositionPIDConfig)
              .setSupplyCurrentLimit(40.0)
              .setStatorCurrentLimit(80.0)
              .setBootPositionOffset(Constants.EleArm.startingAngle)
              .setReverseSoftLimit(Constants.EleArm.minAngle)
              .setForwardSoftLimit(Constants.EleArm.maxAngle));

  private QuixTrapezoidProfile m_armProfile;
  private final Timer m_armTimer = new Timer();
  private State m_armState = new State(m_armAngleMotor.getSensorPosition(), 0.0);

  private boolean m_beamBreakLastState = false;
  private Double m_beamBreakFeedPosition = null;
  private Double m_beamBreakRedirectPosition = null;

  public EleArmSubsystem(
      Link2d EleArmArmViz,
      Link2d EleArmTopWheelViz,
      Link2d EleArmBottomWheelViz,
      Link2d EleArmFeedRollerViz,
      Link2d EleArmRedirectRollerViz) {

    m_armProfile =
        new QuixTrapezoidProfile(
            Constants.EleArm.armTrapConstraints,
            new State(Constants.EleArm.startingAngle, 0.0),
            m_armState);
    m_armTimer.start();
    // Show scheduler status in SmartDashboard.
    SmartDashboard.putData(this);

    // Setup viz.
    m_EleArmArmViz = EleArmArmViz;
    m_EleArmTopWheelViz = EleArmTopWheelViz;
    m_EleArmBottomWheelViz = EleArmBottomWheelViz;
    m_EleArmFeedRollerViz = EleArmFeedRollerViz;
    m_EleArmRedirectRollerViz = EleArmRedirectRollerViz;
  }

  public boolean hasPiece() {
    return m_beamBreak.get();
  }

  public boolean readyForIntake() {
    return !hasPiece()
        && isAtAngle(Constants.EleArm.intakeAngle, Constants.EleArm.intakeAngleTolerance);
  }

  public double getArmAngle() {
    return m_armAngleMotor.getSensorPosition();
  }

  public boolean isAtLaunchVelocity(double launchVelocity, double tolerance) {
    return Math.abs(launchVelocity - m_upperLaunchMotor.getSensorVelocity()) <= tolerance
        && Math.abs(launchVelocity - m_lowerLaunchMotor.getSensorVelocity()) <= tolerance;
  }

  public boolean isAtAutoStartVelocity() {
    return m_upperLaunchMotor.getSensorVelocity() > Constants.EleArm.autoLaunchStartVelocity
        && m_lowerLaunchMotor.getSensorVelocity() > Constants.EleArm.autoLaunchStartVelocity;
  }

  public boolean isAtAngle(double angle, double tolerance) {
    return Math.abs(angle - m_armAngleMotor.getSensorPosition()) <= tolerance;
  }

  public void setArmAngle(double targetArmAngle) {
    m_armProfile =
        new QuixTrapezoidProfile(
            Constants.EleArm.armTrapConstraints,
            new State(
                MathUtils.clamp(
                    targetArmAngle, Constants.EleArm.minAngle, Constants.EleArm.maxAngle),
                0.0),
            m_armState);
    m_armTimer.reset();
  }

  public void setArmAngleSlow(double targetArmAngle) {
    m_armProfile =
        new QuixTrapezoidProfile(
            Constants.EleArm.armSlowTrapConstraints,
            new State(
                MathUtils.clamp(
                    targetArmAngle, Constants.EleArm.minAngle, Constants.EleArm.maxAngle),
                0.0),
            m_armState);
    m_armTimer.reset();
  }

  public void setFeedVelocity(double velocity) {
    final double feedffVolts = Constants.EleArm.feedRollerFeedforward.calculate(velocity);
    if (velocity == 0.0) {
      m_feedRollerMotor.setPercentOutput(0.0);
    } else {
      m_feedRollerMotor.setVelocitySetpoint(
          Constants.EleArm.feedVelocityPIDSlot, velocity, feedffVolts);
    }
  }

  public void setFeedPower(double power) {
    m_feedRollerMotor.setPercentOutput(power);
  }

  public void setRedirectPower(double power) {
    m_redirectRollerMotor.setPercentOutput(power);
  }

  public void stopFeed() {
    m_feedRollerMotor.setPercentOutput(0.0);
  }

  public void setRedirectVelocity(double velocity) {
    final double redirectffVolts = Constants.EleArm.redirectRollerFeedforward.calculate(velocity);
    if (velocity == 0.0) {
      m_redirectRollerMotor.setPercentOutput(0.0);
    } else {
      m_redirectRollerMotor.setVelocitySetpoint(
          Constants.EleArm.redirectVelocityPIDSlot, velocity, redirectffVolts);
    }
  }

  public void moveFeedAndRedirectToPositionOffset(double rads) {
    if (m_beamBreakFeedPosition != null && m_beamBreakRedirectPosition != null) {
      m_feedRollerMotor.setPositionSetpoint(
          Constants.EleArm.feedPositionPIDSlot, m_beamBreakFeedPosition + rads);
      m_redirectRollerMotor.setPositionSetpoint(
          Constants.EleArm.redirectPositionPIDSlot, m_beamBreakRedirectPosition + rads);
    }
  }

  /** Velocity in rad/s */
  public void setLaunchVelocity(double velocity) {
    final double ffVolts = Constants.EleArm.EleArmFeedforward.calculate(velocity);

    if (velocity == 0.0) {
      m_upperLaunchMotor.setPercentOutput(0.0);
      m_lowerLaunchMotor.setPercentOutput(0.0);
    } else {
      m_upperLaunchMotor.setVelocitySetpoint(
          Constants.EleArm.EleArmVelocityPIDSlot, velocity, ffVolts);
      m_lowerLaunchMotor.setVelocitySetpoint(
          Constants.EleArm.EleArmVelocityPIDSlot, velocity, ffVolts);
    }
  }

  public double setLinearLaunchVelocity(double metersPerSecond) {
    // Linear approximation of launch velocity.
    final double radsPerSec =
        (metersPerSecond / Constants.EleArm.shotVelocity) * Constants.EleArm.launchVelocity;
    setLaunchVelocity(radsPerSec);
    return radsPerSec;
  }

  public void disabledInit() {
    m_armAngleMotor.setBrakeMode(true);
  }

  public void disabledExit() {
    m_armAngleMotor.setBrakeMode(false);
  }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    if (DriverStation.isDisabled()) {
      // Update state to sensor state when disabled to prevent jumps on enable.
      m_armState = new State(m_armAngleMotor.getSensorPosition(), 0.0);
      setArmAngle(m_armAngleMotor.getSensorPosition());
    }

    // Track the position where the beam break is broken.
    if (m_beamBreak.get()) {
      if (!m_beamBreakLastState) {
        m_beamBreakFeedPosition = m_feedRollerMotor.getSensorPosition();
        m_beamBreakRedirectPosition = m_redirectRollerMotor.getSensorPosition();
      }
      m_beamBreakLastState = true;
    } else {
      m_beamBreakFeedPosition = null;
      m_beamBreakRedirectPosition = null;
      m_beamBreakLastState = false;
    }

    SmartDashboard.putBoolean("EleArm: Beam Break", m_beamBreak.get());

    m_armState = m_armProfile.calculate(m_armTimer.get());
    m_armAngleMotor.setPositionSetpoint(
        Constants.EleArm.armPositionPIDSlot,
        m_armState.position,
        // Arm angle is defined as positive when the EleArm is pointed up, but the CG is on the
        // other side with some offset, so we need to negate the angle and voltage for FF.
        -Constants.EleArm.armFeedForward.calculate(
            -m_armState.position + Constants.EleArm.cgOffset, -m_armState.velocity));

    SmartDashboard.putNumber(
        "EleArm: Current Arm Angle (deg)",
        Units.radiansToDegrees(m_armAngleMotor.getSensorPosition()));
    SmartDashboard.putNumber(
        "EleArm: Target Arm Angle (deg)", Units.radiansToDegrees(m_armState.position));
    SmartDashboard.putNumber(
        "EleArm: Arm Angle Error (deg)",
        Units.radiansToDegrees(m_armState.position - m_armAngleMotor.getSensorPosition()));

    SmartDashboard.putNumber(
        "EleArm: Current Redirect Roller Velocity (rad per sec)",
        m_redirectRollerMotor.getSensorVelocity());
    SmartDashboard.putNumber(
        "EleArm: Current Feed Roller Velocity (rad per sec)",
        m_feedRollerMotor.getSensorVelocity());
    SmartDashboard.putNumber(
        "EleArm: Current Top Launch Wheel Velocity (rad per sec)",
        m_upperLaunchMotor.getSensorVelocity());
    SmartDashboard.putNumber(
        "EleArm: Current Lower Launch Wheel Velocity (rad per sec)",
        m_lowerLaunchMotor.getSensorVelocity());

    SmartDashboard.putNumber(
        "EleArm: Current Feed Roller Current (A)", m_feedRollerMotor.getStatorCurrent());
    SmartDashboard.putNumber(
        "EleArm: Current Top Launch Wheel Current (A)", m_upperLaunchMotor.getStatorCurrent());
    SmartDashboard.putNumber(
        "EleArm: Current Lower Launch Wheel Current (A)", m_lowerLaunchMotor.getStatorCurrent());

    m_upperLaunchMotor.logMotorState();
    m_lowerLaunchMotor.logMotorState();
    m_feedRollerMotor.logMotorState();
    m_redirectRollerMotor.logMotorState();
    m_armAngleMotor.logMotorState();
  }

  // --- BEGIN STUFF FOR SIMULATION ---
  // Note that the arm simulated backwards because the sim requires zero angle to be gravity acting
  // down on the arm, but gravity acts "up" on the arm from the perspective of the launch angle.
  private static final SingleJointedArmSim m_armSim =
      new SingleJointedArmSim(
          DCMotor.getKrakenX60Foc(1),
          Constants.EleArm.armMotorRatio.reduction(),
          Constants.EleArm.simArmMOI,
          Constants.EleArm.simArmCGLength,
          -Constants.EleArm.maxAngle, // Arm is simulated backwards
          -Constants.EleArm.minAngle, // Arm is simulated backwards
          true, // Simulate gravity
          Constants.EleArm.startingAngle);

  

 static final DCMotor m_simMotorTop = DCMotor.getKrakenX60Foc(1);
  private static final FlywheelSim m_rollerSimTop =
      new FlywheelSim(
          LinearSystemId.createFlywheelSystem(
            m_simMotorTop,
              Constants.EleArm.simRollerMOI,
              Constants.EleArm.upperMotorRatio.reduction()),
              m_simMotorTop);

static final DCMotor m_simMotorBottom = DCMotor.getKrakenX60Foc(1);
  private static final FlywheelSim m_rollerSimBottom =
      new FlywheelSim(
          LinearSystemId.createFlywheelSystem(
            m_simMotorBottom,
              Constants.EleArm.simRollerMOI,
              Constants.EleArm.upperMotorRatio.reduction()),
              m_simMotorBottom);


  // Visualization
  private final Link2d m_EleArmArmViz;
  private final Link2d m_EleArmTopWheelViz;
  private final Link2d m_EleArmBottomWheelViz;
  private final Link2d m_EleArmFeedRollerViz;
  private final Link2d m_EleArmRedirectRollerViz;

  @Override
  public void simulationPeriodic() {
    // This method will be called once per scheduler run during simulation
    m_armSim.setInput(-m_armAngleMotor.getPercentOutput() * RobotController.getBatteryVoltage());
    m_armSim.update(TimedRobot.kDefaultPeriod);
    // Arm is simulated backwards because gravity acting on a horizontal arm needs to be at
    // zero degrees
    m_armAngleMotor.setSimSensorPositionAndVelocity(
        -m_armSim.getAngleRads() - Constants.EleArm.startingAngle,
        m_armSim.getVelocityRadPerSec(),
        TimedRobot.kDefaultPeriod,
        Constants.EleArm.armMotorRatio);

        m_rollerSimTop.setInput(
        m_upperLaunchMotor.getPercentOutput() * RobotController.getBatteryVoltage());
        m_rollerSimTop.update(TimedRobot.kDefaultPeriod);
    m_upperLaunchMotor.setSimSensorVelocity(
        m_rollerSimTop.getAngularVelocityRadPerSec(),
        TimedRobot.kDefaultPeriod,
        Constants.EleArm.upperMotorRatio);

        m_rollerSimBottom.setInput(
        m_lowerLaunchMotor.getPercentOutput() * RobotController.getBatteryVoltage());
        m_rollerSimBottom.update(TimedRobot.kDefaultPeriod);
    m_lowerLaunchMotor.setSimSensorVelocity(
        m_rollerSimBottom.getAngularVelocityRadPerSec(),
        TimedRobot.kDefaultPeriod,
        Constants.EleArm.lowerMotorRatio);

    

    m_EleArmArmViz.setRelativeTransform(
        new Transform2d(
            Constants.Viz.EleArmArmPivotX,
            0.0,
            // TODO: Figure out how to do this without hardcoding
            Rotation2d.fromRadians(
                m_armSim.getAngleRads() - Constants.Viz.elevatorAngle.getRadians())));
    m_EleArmTopWheelViz.setRelativeTransform(
        new Transform2d(
            Constants.Viz.EleArmWheelX,
            Constants.Viz.EleArmTopWheelY,
            Rotation2d.fromRadians(
                m_EleArmTopWheelViz.getRelativeTransform().getRotation().getRadians()
                    + m_rollerSimTop.getAngularVelocityRadPerSec()
                        * Constants.Viz.angularVelocityScalar)));
    m_EleArmBottomWheelViz.setRelativeTransform(
        new Transform2d(
            Constants.Viz.EleArmWheelX,
            Constants.Viz.EleArmBottomWheelY,
            Rotation2d.fromRadians(
                m_EleArmBottomWheelViz.getRelativeTransform().getRotation().getRadians()
                    + m_rollerSimBottom.getAngularVelocityRadPerSec()
                        * Constants.Viz.angularVelocityScalar)));
   
  }
  // --- END STUFF FOR SIMULATION ---
}
