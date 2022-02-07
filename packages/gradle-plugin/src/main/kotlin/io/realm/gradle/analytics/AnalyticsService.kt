/*
 * Copyright 2022 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm.gradle.analytics

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import org.gradle.api.services.BuildServiceParameters

import org.gradle.api.services.BuildService
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationResult
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult

/**
 * Analytics Build Service responsible for triggering analytics at the correct time.
 * Build Services are still very much experimental, so additional logging has been added
 * to this class to catch catches where types are different than expected. We do _NOT_
 * want analytics to take down a users build, so exceptions are avoided on purpose.
 *
 * Build Services was added in Gradle 6.1. They can be called by multiple tasks, so must
 * be implemented to be thread-safe
 *
 * @see https://docs.gradle.org/current/userguide/build_services.html
 */
abstract class AnalyticsService : BuildService<BuildServiceParameters.None>, OperationCompletionListener {

    private val logger: Logger = Logging.getLogger("realm-build-service")
    private var analytics: RealmAnalytics? = null

    /**
     * Only lifecycle event currently available in Build Services.
     */
    override fun onFinish(event: FinishEvent?) {
        try {
            if (event == null) {
                logger.warn("Null event received. This should never happen.")
                return
            }
            when(event) {
                is TaskFinishEvent -> handleTaskResult(event)
                else -> {
                    logger.warn("Unknown event type: ${event.javaClass.name}")
                }
            }
        } catch (ex: Exception) {
            logger.warn("Unexpected error: ${ex.toString()}")
        }
    }

    private fun handleTaskResult(taskEvent: TaskFinishEvent) {
        when(val result: TaskOperationResult = taskEvent.result) {
            is TaskSkippedResult -> { /* Ignore skipped tasks to avoid excessive work during incremental builds */ }
            is TaskFailureResult -> { filterResultAndSendAnalytics(taskEvent) }
            is TaskSuccessResult -> { filterResultAndSendAnalytics(taskEvent) }
            else -> {
                logger.warn("Unknown task type: ${result.javaClass.name}")
            }
        }
    }


    private fun filterResultAndSendAnalytics(taskEvent: TaskFinishEvent) {
        // We use `compile<XXX>` tasks as a heuristic for a "build". This will not detect builds
        // that fail very early or incremental builds with no code change, but neither will it
        // trigger for tasks unrelated to building code. A normal build consists of multiple
        // compile tasks, but the RealmAnalytics class tracks this and only send analytics once.
        if (taskEvent.descriptor.name.contains("compile", true)) {
            analytics?.sendAnalyticsData()
        }
    }

    /**
     * In order to support the Gradle Configuration Cache, this method must be called during
     * the Configuration Phase in `afterEvaluate`. It isn't allowed to store a reference to
     * the `project` property.
     *
     * This method is responsible for gathering all the analytics data we are sending.
     */
    @Synchronized
    fun collectAnalyticsData(project: Project) {
        analytics = RealmAnalytics()
        analytics!!.gatherAnalyticsDataIfNeeded(project)
    }
}

