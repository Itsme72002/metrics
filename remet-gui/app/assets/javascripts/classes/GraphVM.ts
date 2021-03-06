/*
 * Copyright 2014 Groupon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


///<reference path="../libs/flotr2/flotr2.d.ts"/>

import ConnectionVM = require('./ConnectionVM');
import StatisticView = require('./StatisticView');
import Series = require('./Series');
import ViewDuration = require('./ViewDuration');
import GraphSpec = require('./GraphSpec');

import Flotr = require('flotr2');

// requestAnimFrame shim with setTimeout fallback
window.requestAnimationFrame = (function () {
    return window.requestAnimationFrame ||
        (<any>window).webkitRequestAnimationFrame ||
        (<any>window).mozRequestAnimationFrame ||
        (<any>window).oRequestAnimationFrame ||
        (<any>window).msRequestAnimationFrame ||
        function (callback) {
            window.setTimeout(callback, 1000 / 60);
        };
})();

class GraphVM implements StatisticView {
    id: string;
    name: string;
    started: boolean = false;
    container: HTMLElement = null;
    data: Series[] = [];
    dataStreams: { [key: string]: number } = {};
    stop: boolean = false;
    paused: boolean = false;
    targetFrameRate: number = 60;
    duration: number = 30000;
    endAt: number = 0;
    dataLength: number = 600000;
    spec: GraphSpec;

    constructor(id: string, name: string, spec: GraphSpec) {
        this.id = id;
        this.name = name;
        this.spec = spec;
    }

    disconnectConnection(cvm: ConnectionVM) {
        var index = this.dataStreams[cvm.server];
        if (index != undefined) {
            delete this.dataStreams[cvm.server];
            var series = this.data.splice(index, 1);
            series[0].colorSubscription.dispose();

            //we now need to re-index dataStreams
            //any element with an index > the var index needs to be decremented
            for (var i in this.dataStreams) {
                var old = this.dataStreams[i];
                if (old > index) {
                    this.dataStreams[i] = old - 1;
                }
            }
        }
    }

    shutdown() {
        this.stop = true;
    }

    setViewDuration(window: ViewDuration) {
        var endTime = this.dataLength - window.end;
        this.duration = window.end - window.start;
        this.endAt = endTime;
    }

    niceName(id: string): string {
        return id.replace(/:/g, " ");
    }

    updateColor(cvm: ConnectionVM): void {
        var index = this.dataStreams[cvm.server];
        this.data[index].color = cvm.color();
        this.data[index].points.fillOpacity = cvm.alpha();
    }

    postData(server: string, timestamp: number, dataValue: number, cvm: ConnectionVM) {
        var index = this.dataStreams[cvm.server];
        if (index == undefined) {
            index = this.data.length;
            this.dataStreams[cvm.server] = index;
            var series = new Series(cvm.server, cvm.color());
            series.colorSubscription = cvm.color.subscribe((color: String) => {
                this.updateColor(cvm);
            });
            this.data.push(series);
        }


        if (this.data[index].data.length == 0 || this.data[index].data[this.data[index].data.length - 1][0] < timestamp) {
            this.data[index].data.push([timestamp, dataValue]);
        }
    }

    start() {
        if (this.started == true) {
            return;
        }
        this.started = true;
        this.container = document.getElementById(this.id);

        var previousTimestamp: number = 0.0;
        var animate = (timestamp: number) => {

            if (this.stop) {
                return;
            }

            if (this.paused || this.container.clientWidth == 0) {
                // Delayed animate
                var stepTimeInMillis = 100;
                setTimeout(
                    () => {
                        window.requestAnimationFrame(animate);
                    },
                    stepTimeInMillis);
                return;
            }

            //set min and max
            var graphMin = 1000000000;
            var graphMax = -1000000000;

            var now = new Date().getTime() - 1000;
            var graphEnd = now - this.endAt;
            var graphStart = graphEnd - this.duration;
            for (var series = 0; series < this.data.length; series++) {
                //Deleted entries are not removed from the array in order to
                //preserve the hash of name => index
                if (this.data[series] === undefined) {
                    continue;
                }
                //shift the data off the array that is too old
                while (this.data[series].data[1] != undefined && this.data[series].data[1][0] < graphEnd - this.dataLength) {
                    this.data[series].data.shift();
                }

                //find the indexes in the window
                var lower = this.data[series].data.length;
                var upper = 0;
                for (var iter = this.data[series].data.length - 1; iter >= 0; iter--) {
                    var dataTimestamp = this.data[series].data[iter][0];
                    if (dataTimestamp >= graphStart && dataTimestamp <= graphEnd) {
                        if (iter < lower) {
                            lower = iter;
                        }
                        if (iter > upper) {
                            upper = iter;
                        }
                    }
                }

                if (lower > 0) {
                    lower--;
                }
                if (upper < this.data[series].data.length - 1) {
                    upper++;
                }

                for (var back = lower; back <= upper; back++) {
                    //it's in our view window
                    var dataVal = this.data[series].data[back][1];
                    if (dataVal > graphMax) {
                        graphMax = dataVal;
                    }
                    if (dataVal < graphMin) {
                        graphMin = dataVal;
                    }
                }
            }
            if (graphMax == graphMin) {
                graphMin--;
                graphMax++;
            } else if (graphMin > graphMax) {
                //This is the case with no data
                graphMin = 0;
                graphMax = 1;
            }
            else {
                var spread = graphMax - graphMin;
                var buffer = spread / 10;
                graphMin -= buffer;
                graphMax += buffer;
            }

            // Draw Graph
            Flotr.draw(this.container, this.data, {
                yaxis: {
                    max: graphMax,
                    min: graphMin
                },
                xaxis: {
                    mode: 'time',
                    noTicks: 3,
                    min: graphStart,
                    max: graphEnd,
                    timeMode: "local"

                },
                title: this.name,
                HtmlText: false,
                mouse: {
                    track: false,
                    sensibility: 8,
                    radius: 15
                },
                legend: {
                    show: false
                }
            });

            // Render
            if (previousTimestamp != null) {
                var currentRate = 1000 / (timestamp - previousTimestamp);
                if (currentRate > this.targetFrameRate) {
                    // Delayed animate
                    var stepTimeInMillis = 1000 * (1 / this.targetFrameRate - 1 / currentRate);
                    previousTimestamp = timestamp;
                    setTimeout(
                        () => {
                            window.requestAnimationFrame(animate);
                        },
                        stepTimeInMillis);
                    return;
                }
            }
            previousTimestamp = timestamp;
            window.requestAnimationFrame(animate);
        };

        window.requestAnimationFrame(animate);
    }
}

export = GraphVM;
