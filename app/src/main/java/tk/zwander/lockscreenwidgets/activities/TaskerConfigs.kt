package tk.zwander.lockscreenwidgets.activities

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutput
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionNoOutput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.runner.*
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.util.FrameInstances
import tk.zwander.common.util.setThemedContent
import tk.zwander.lockscreenwidgets.compose.tasker.ChooseFrameIDsLayout
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate

@TaskerInputRoot
data class FrameIDs @JvmOverloads constructor(
    @field:TaskerInputField("ids", labelResIdName = "frame_ids")
    var ids: ArrayList<String> = arrayListOf(),
)

abstract class BaseTaskerFrameActivity : BaseActivity(), TaskerPluginConfig<FrameIDs> {
    protected abstract val helper: TaskerPluginConfigHelperNoOutput<FrameIDs, *>

    override val context: Context
        get() = this

    override val inputForTasker: TaskerInput<FrameIDs>
        get() = TaskerInput(FrameIDs(ids = ArrayList(selectedIds.map { it.toString() })))

    override fun assignFromInput(input: TaskerInput<FrameIDs>) {
        Log.e("LSW", "Assigning ${input.regular}")
        selectedIds = input.regular.ids.mapNotNull { it.toIntOrNull() }
    }

    protected var selectedIds by mutableStateOf(listOf<Int>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        helper.onCreate()

        setThemedContent {
            ChooseFrameIDsLayout(
                initialSelectedIds = selectedIds,
                onSave = {
                    Log.e("LSW", "onSave")
                    selectedIds = it
                    helper.finishForTasker()
                },
                onCancel = {
                    helper.onBackPressed()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.e("LSW", "Destroying ${selectedIds}")
    }
}

class TaskerCanShowActivity : BaseTaskerFrameActivity() {
    override val helper by lazy { CanShowHelper(this) }

    class CanShowHelper(config: TaskerPluginConfig<FrameIDs>) : TaskerPluginConfigHelperNoOutput<FrameIDs, CanShowRunner>(config) {
        override val inputClass: Class<FrameIDs> = FrameIDs::class.java
        override val runnerClass: Class<CanShowRunner> = CanShowRunner::class.java
    }

    class CanShowRunner : TaskerPluginRunnerActionNoOutput<FrameIDs>() {
        override fun run(context: Context, input: TaskerInput<FrameIDs>): TaskerPluginResult<Unit> {
            val frameIds = input.regular.ids.orAllIds(context)
            frameIds.forEach { id ->
                FrameSpecificPreferences[id].canShowFromTasker = true
            }

            return TaskerPluginResultSucess()
        }
    }
}

class TaskerCanNotShowActivity : BaseTaskerFrameActivity() {
    override val helper by lazy { CanNotShowHelper(this) }

    class CanNotShowHelper(config: TaskerPluginConfig<FrameIDs>) : TaskerPluginConfigHelperNoOutput<FrameIDs, CanNotShowRunner>(config) {
        override val inputClass: Class<FrameIDs> = FrameIDs::class.java
        override val runnerClass: Class<CanNotShowRunner> = CanNotShowRunner::class.java
    }

    class CanNotShowRunner : TaskerPluginRunnerActionNoOutput<FrameIDs>() {
        override fun run(context: Context, input: TaskerInput<FrameIDs>): TaskerPluginResult<Unit> {
            val frameIds = input.regular.ids.orAllIds(context)
            frameIds.forEach { id ->
                FrameSpecificPreferences[id].canShowFromTasker = false
            }
            return TaskerPluginResultSucess()
        }
    }
}

class TaskerForceShowActivity : BaseTaskerFrameActivity() {
    override val helper by lazy { ForceShowHelper(this) }

    class ForceShowHelper(config: TaskerPluginConfig<FrameIDs>) : TaskerPluginConfigHelperNoOutput<FrameIDs, ForceShowRunner>(config) {
        override val inputClass: Class<FrameIDs> = FrameIDs::class.java
        override val runnerClass: Class<ForceShowRunner> = ForceShowRunner::class.java
    }

    class ForceShowRunner : TaskerPluginRunnerActionNoOutput<FrameIDs>() {
        override fun run(context: Context, input: TaskerInput<FrameIDs>): TaskerPluginResult<Unit> {
            val frameIds = input.regular.ids.orAllIds(context)
            frameIds.forEach { id ->
                FrameSpecificPreferences[id].forceShow = true
            }

            return TaskerPluginResultSucess()
        }
    }
}

class TaskerUnForceShowActivity : BaseTaskerFrameActivity() {
    override val helper by lazy { UnForceShowHelper(this) }

    class UnForceShowHelper(config: TaskerPluginConfig<FrameIDs>) : TaskerPluginConfigHelperNoOutput<FrameIDs, UnForceShowRunner>(config) {
        override val inputClass: Class<FrameIDs> = FrameIDs::class.java
        override val runnerClass: Class<UnForceShowRunner> = UnForceShowRunner::class.java
    }

    class UnForceShowRunner : TaskerPluginRunnerActionNoOutput<FrameIDs>() {
        override fun run(context: Context, input: TaskerInput<FrameIDs>): TaskerPluginResult<Unit> {
            val frameIds = input.regular.ids.orAllIds(context)
            frameIds.forEach { id ->
                FrameSpecificPreferences[id].forceShow = false
            }

            return TaskerPluginResultSucess()
        }
    }
}

class TaskerIsAllowedToShowFrame : BaseTaskerFrameActivity() {
    override val helper by lazy { AllowedToShowHelper(this) }

    class AllowedToShowHelper(config: TaskerPluginConfig<FrameIDs>) : TaskerPluginConfigHelperNoOutput<FrameIDs, AllowedToShowRunner>(config) {
        override val inputClass: Class<FrameIDs>
            get() = FrameIDs::class.java
        override val runnerClass: Class<AllowedToShowRunner>
            get() = AllowedToShowRunner::class.java
    }

    class AllowedToShowRunner : TaskerPluginRunnerConditionNoOutput<FrameIDs, Unit>() {
        override val isEvent: Boolean
            get() = false

        override fun getSatisfiedCondition(
            context: Context,
            input: TaskerInput<FrameIDs>,
            update: Unit?
        ): TaskerPluginResultCondition<Unit> {
            val anySatisfy = input.regular.ids.orAllIds(context).any {
                FrameSpecificPreferences[it].canShowFromTasker
            }

            return if (anySatisfy) {
                TaskerPluginResultConditionSatisfied(context)
            } else {
                TaskerPluginResultConditionUnsatisfied()
            }
        }
    }
}

class TaskerIsShowingFrame : BaseTaskerFrameActivity() {
    override val helper by lazy { IsShowingHelper(this) }

    class IsShowingHelper(config: TaskerPluginConfig<FrameIDs>) : TaskerPluginConfigHelperNoOutput<FrameIDs, IsShowingRunner>(config) {
        override val inputClass: Class<FrameIDs>
            get() = FrameIDs::class.java
        override val runnerClass: Class<IsShowingRunner>
            get() = IsShowingRunner::class.java
    }

    class IsShowingRunner : TaskerPluginRunnerConditionNoOutput<FrameIDs, Unit>() {
        override val isEvent: Boolean
            get() = false

        override fun getSatisfiedCondition(
            context: Context,
            input: TaskerInput<FrameIDs>,
            update: Unit?
        ): TaskerPluginResultCondition<Unit> {
            val ids = input.regular.ids.orAllIds(context)
            val allInstances = FrameInstances.allInstances(context).filter { ids.contains(it.key) }

            return if (allInstances.any { it.value?.isAttached == true }) {
                TaskerPluginResultConditionSatisfied(context)
            } else {
                TaskerPluginResultConditionUnsatisfied()
            }
        }
    }
}

class TaskerIsForceShowingFrame : BaseTaskerFrameActivity() {
    override val helper by lazy { ForcedToShowHelper(this) }

    class ForcedToShowHelper(config: TaskerPluginConfig<FrameIDs>) : TaskerPluginConfigHelperNoOutput<FrameIDs, ForcedToShowRunner>(config) {
        override val inputClass: Class<FrameIDs>
            get() = FrameIDs::class.java
        override val runnerClass: Class<ForcedToShowRunner>
            get() = ForcedToShowRunner::class.java
    }

    class ForcedToShowRunner : TaskerPluginRunnerConditionNoOutput<FrameIDs, Unit>() {
        override val isEvent: Boolean
            get() = false

        override fun getSatisfiedCondition(
            context: Context,
            input: TaskerInput<FrameIDs>,
            update: Unit?
        ): TaskerPluginResultCondition<Unit> {
            val anySatisfy = input.regular.ids.orAllIds(context).any {
                FrameSpecificPreferences[it].forceShow
            }

            return if (anySatisfy) {
                TaskerPluginResultConditionSatisfied(context)
            } else {
                TaskerPluginResultConditionUnsatisfied()
            }
        }
    }
}

private fun List<String>.orAllIds(context: Context): List<Int> {
    return this.mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }
        ?: MainWidgetFrameDelegate.allIds(context)
}
