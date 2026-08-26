import re
with open("feature/player/src/main/java/com/graviton/feature/player/ui/SubtitleSelectorView.kt", "r") as f:
    data = f.read()

data = data.replace('title = stringResource(R.string.delay),\n        value = valueString,', 'title = stringResource(R.string.delay),\n        value = valueString,\n        incrementContentDescription = stringResource(R.string.increase_value),\n        decrementContentDescription = stringResource(R.string.decrease_value),')
data = data.replace('title = stringResource(R.string.speed),\n        value = valueString,', 'title = stringResource(R.string.speed),\n        value = valueString,\n        incrementContentDescription = stringResource(R.string.increase_speed),\n        decrementContentDescription = stringResource(R.string.decrease_speed),')
data = data.replace('title: String,\n    value: String,\n    onValueChange: (String) -> Unit,', 'title: String,\n    value: String,\n    incrementContentDescription: String? = null,\n    decrementContentDescription: String? = null,\n    onValueChange: (String) -> Unit,')
data = data.replace('painter = painterResource(R.drawable.ic_remove),\n                contentDescription = null,', 'painter = painterResource(R.drawable.ic_remove),\n                contentDescription = decrementContentDescription,')
data = data.replace('painter = painterResource(R.drawable.ic_add),\n                contentDescription = null,', 'painter = painterResource(R.drawable.ic_add),\n                contentDescription = incrementContentDescription,')

with open("feature/player/src/main/java/com/graviton/feature/player/ui/SubtitleSelectorView.kt", "w") as f:
    f.write(data)
