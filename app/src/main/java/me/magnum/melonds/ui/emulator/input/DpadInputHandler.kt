package me.magnum.melonds.ui.emulator.input

import me.magnum.melonds.domain.model.Input

class DpadInputHandler(inputListener: IInputListener, enableHapticFeedback: Boolean) : MultiButtonInputHandler(inputListener, enableHapticFeedback) {
    override fun getTopInput() = Input.UP
    override fun getLeftInput() = Input.LEFT
    override fun getBottomInput() = Input.DOWN
    override fun getRightInput() = Input.RIGHT
}