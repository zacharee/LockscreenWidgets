package tk.zwander.widgetdrawer.activities

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
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.widgetdrawer.util.DrawerDelegate

class TaskerShowDrawerActivity : ComponentActivity(), TaskerPluginConfigNoInput {
    private val helper by lazy { ShowHelper(this) }
    override val context: Context
        get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        helper.finishForTasker()
    }

    class ShowHelper(config: TaskerPluginConfigNoInput) : TaskerPluginConfigHelperNoOutputOrInput<ShowRunner>(config) {
        override val runnerClass: Class<ShowRunner> = ShowRunner::class.java
    }

    class ShowRunner : TaskerPluginRunnerActionNoOutputOrInput() {
        override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<Unit> {
            context.eventManager.sendEvent(Event.ShowDrawer)
            return TaskerPluginResultSucess()
        }
    }
}

class TaskerHideDrawerActivity : ComponentActivity(), TaskerPluginConfigNoInput {
    private val helper by lazy { HideHelper(this) }
    override val context: Context
        get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        helper.finishForTasker()
    }

    class HideHelper(config: TaskerPluginConfigNoInput) : TaskerPluginConfigHelperNoOutputOrInput<HideRunner>(config) {
        override val runnerClass: Class<HideRunner> = HideRunner::class.java
    }

    class HideRunner : TaskerPluginRunnerActionNoOutputOrInput() {
        override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<Unit> {
            context.eventManager.sendEvent(Event.CloseDrawer)
            return TaskerPluginResultSucess()
        }
    }
}

class TaskerIsShowingDrawer : ComponentActivity(), TaskerPluginConfigNoInput {
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
            return if (DrawerDelegate.peekInstance(context)?.isAttached == true) {
                TaskerPluginResultConditionSatisfied(context)
            } else {
                TaskerPluginResultConditionUnsatisfied()
            }
        }
    }
}
