package tk.zwander.lockscreenwidgets.activities

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutputOrInput
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionNoOutputOrInputOrUpdateState
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutputOrInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperStateNoOutputOrInputOrUpdate
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.util.WidgetFrameDelegate

class TaskerCanShowActivity : ComponentActivity(), TaskerPluginConfigNoInput {
    private val helper by lazy { CanShowHelper(this) }
    override val context: Context
        get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        helper.finishForTasker()
    }

    class CanShowHelper(config: TaskerPluginConfigNoInput) : TaskerPluginConfigHelperNoOutputOrInput<CanShowRunner>(config) {
        override val runnerClass: Class<CanShowRunner> = CanShowRunner::class.java
    }

    class CanShowRunner : TaskerPluginRunnerActionNoOutputOrInput() {
        override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<Unit> {
            context.prefManager.canShowFrameFromTasker = true
            return TaskerPluginResultSucess()
        }
    }
}

class TaskerCanNotShowActivity : ComponentActivity(), TaskerPluginConfigNoInput {
    private val helper by lazy { CanNotShowHelper(this) }
    override val context: Context
        get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        helper.finishForTasker()
    }

    class CanNotShowHelper(config: TaskerPluginConfigNoInput) : TaskerPluginConfigHelperNoOutputOrInput<CanNotShowRunner>(config) {
        override val runnerClass: Class<CanNotShowRunner> = CanNotShowRunner::class.java
    }

    class CanNotShowRunner : TaskerPluginRunnerActionNoOutputOrInput() {
        override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<Unit> {
            context.prefManager.canShowFrameFromTasker = false
            return TaskerPluginResultSucess()
        }
    }
}

class TaskerForceShowActivity : ComponentActivity(), TaskerPluginConfigNoInput {
    private val helper by lazy { ForceShowHelper(this) }
    override val context: Context
        get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        helper.finishForTasker()
    }

    class ForceShowHelper(config: TaskerPluginConfigNoInput) : TaskerPluginConfigHelperNoOutputOrInput<ForceShowRunner>(config) {
        override val runnerClass: Class<ForceShowRunner> = ForceShowRunner::class.java
    }

    class ForceShowRunner : TaskerPluginRunnerActionNoOutputOrInput() {
        override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<Unit> {
            context.prefManager.forceShowFrame = true
            return TaskerPluginResultSucess()
        }
    }
}

class TaskerUnForceShowActivity : ComponentActivity(), TaskerPluginConfigNoInput {
    private val helper by lazy { UnForceShowHelper(this) }
    override val context: Context
        get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        helper.finishForTasker()
    }

    class UnForceShowHelper(config: TaskerPluginConfigNoInput) : TaskerPluginConfigHelperNoOutputOrInput<UnForceShowRunner>(config) {
        override val runnerClass: Class<UnForceShowRunner> = UnForceShowRunner::class.java
    }

    class UnForceShowRunner : TaskerPluginRunnerActionNoOutputOrInput() {
        override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<Unit> {
            context.prefManager.forceShowFrame = false
            return TaskerPluginResultSucess()
        }
    }
}

class TaskerIsAllowedToShowFrame : ComponentActivity(), TaskerPluginConfigNoInput {
    private val helper by lazy { AllowedToShowHelper(this) }

    override val context: Context
        get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        helper.finishForTasker()
    }

    class AllowedToShowHelper(config: TaskerPluginConfigNoInput) : TaskerPluginConfigHelperStateNoOutputOrInputOrUpdate<AllowedToShowRunner>(config) {
        override val runnerClass: Class<AllowedToShowRunner>
            get() = AllowedToShowRunner::class.java
    }

    class AllowedToShowRunner : TaskerPluginRunnerConditionNoOutputOrInputOrUpdateState() {
        override fun getSatisfiedCondition(
            context: Context,
            input: TaskerInput<Unit>,
            update: Unit?
        ): TaskerPluginResultCondition<Unit> {
            return if (context.prefManager.canShowFrameFromTasker) {
                TaskerPluginResultConditionSatisfied(context)
            } else {
                TaskerPluginResultConditionUnsatisfied()
            }
        }
    }
}

class TaskerIsShowingFrame : ComponentActivity(), TaskerPluginConfigNoInput {
    private val helper by lazy { IsShowingHelper(this) }

    override val context: Context
        get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        helper.finishForTasker()
    }

    class IsShowingHelper(config: TaskerPluginConfigNoInput) : TaskerPluginConfigHelperStateNoOutputOrInputOrUpdate<IsShowingRunner>(config) {
        override val runnerClass: Class<IsShowingRunner>
            get() = IsShowingRunner::class.java
    }

    class IsShowingRunner : TaskerPluginRunnerConditionNoOutputOrInputOrUpdateState() {
        override fun getSatisfiedCondition(
            context: Context,
            input: TaskerInput<Unit>,
            update: Unit?
        ): TaskerPluginResultCondition<Unit> {
            return if (WidgetFrameDelegate.peekInstance(context)?.isAttached == true) {
                TaskerPluginResultConditionSatisfied(context)
            } else {
                TaskerPluginResultConditionUnsatisfied()
            }
        }
    }
}

class TaskerIsForceShowingFrame : ComponentActivity(), TaskerPluginConfigNoInput {
    private val helper by lazy { ForcedToShowHelper(this) }

    override val context: Context
        get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        helper.finishForTasker()
    }

    class ForcedToShowHelper(config: TaskerPluginConfigNoInput) : TaskerPluginConfigHelperStateNoOutputOrInputOrUpdate<ForcedToShowRunner>(config) {
        override val runnerClass: Class<ForcedToShowRunner>
            get() = ForcedToShowRunner::class.java
    }

    class ForcedToShowRunner : TaskerPluginRunnerConditionNoOutputOrInputOrUpdateState() {
        override fun getSatisfiedCondition(
            context: Context,
            input: TaskerInput<Unit>,
            update: Unit?
        ): TaskerPluginResultCondition<Unit> {
            return if (context.prefManager.forceShowFrame) {
                TaskerPluginResultConditionSatisfied(context)
            } else {
                TaskerPluginResultConditionUnsatisfied()
            }
        }
    }
}
