import 'package:flutter/widgets.dart';

import 'tracker.dart';

/// Registers [GisoTracker] as a [WidgetsBindingObserver] for lifecycle events.
class GisoLifecycleBinding {
  static void attach() => GisoTracker.instance.attachLifecycle();
  static void detach() => GisoTracker.instance.detachLifecycle();
}
