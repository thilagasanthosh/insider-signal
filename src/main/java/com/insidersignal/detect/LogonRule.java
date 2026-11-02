package com.insidersignal.detect;

import com.insidersignal.event.WinEvent;
import java.util.Optional;

/** A detection evaluated against each successful logon. */
public interface LogonRule {

  Optional<Alert> evaluate(WinEvent logon);
}
