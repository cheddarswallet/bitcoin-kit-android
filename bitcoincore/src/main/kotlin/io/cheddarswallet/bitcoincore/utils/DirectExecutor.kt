package io.cheddarswallet.bitcoincore.utils

import java.util.concurrent.Executor

class DirectExecutor : Executor {

    override fun execute(command: Runnable) {
        command.run()
    }

}
