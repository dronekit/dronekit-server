package com.geeksville.dapi

import com.newrelic.api.agent.NewRelic
import com.geeksville.util.AnalyticsAdapter
import grizzled.slf4j.Logging
import com.geeksville.util.AnalyticsService

object NewRelicAnalyticsAdapter extends AnalyticsAdapter with Logging {
  def reportException(msg: String, ex: Throwable) {
    error(s"Exception occurred (msg=$msg): $ex")
    NewRelic.noticeError(ex)
  }

  def install() {
    AnalyticsService.handler = this
  }
}