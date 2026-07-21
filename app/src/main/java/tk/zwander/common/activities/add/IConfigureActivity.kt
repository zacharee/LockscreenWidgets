package tk.zwander.common.activities.add

import tk.zwander.common.data.provider.ICurrentWidgetsProvider
import tk.zwander.common.data.provider.IDrawerProvider
import tk.zwander.common.data.provider.IFrameProvider
import tk.zwander.common.data.provider.IRowColumProvider
import tk.zwander.common.data.provider.IWidthHeightProvider

sealed interface IConfigureActivity : IRowColumProvider, ICurrentWidgetsProvider, IWidthHeightProvider

interface IFrameConfigureActivity : IConfigureActivity, IFrameProvider

interface IDrawerConfigureActivity : IConfigureActivity, IDrawerProvider
