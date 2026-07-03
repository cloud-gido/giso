import 'package:flutter/widgets.dart';

import 'tracker.dart';
import 'types.dart';

/// Page shell: calls [GisoTracker.enterPage] on mount and [GisoTracker.exitPage] on dispose.
class TrackedPage extends StatefulWidget {
  const TrackedPage({
    super.key,
    required this.pgid,
    required this.child,
    this.pgParams,
    this.pt,
  });

  final String pgid;
  final Params? pgParams;
  final Passthrough? pt;
  final Widget child;

  @override
  State<TrackedPage> createState() => _TrackedPageState();
}

class _TrackedPageState extends State<TrackedPage> {
  @override
  void initState() {
    super.initState();
    GisoTracker.instance.enterPage(widget.pgid, widget.pgParams, widget.pt);
  }

  @override
  void dispose() {
    GisoTracker.instance.exitPage();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
