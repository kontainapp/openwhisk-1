FROM openwhisk/action-nodejs-v12:1.14.0-incubating AS ownode
FROM kontain/node-km:master AS kontain
FROM scratch

COPY --from=ownode /nodejsAction /nodejsAction
COPY --from=ownode /node_modules /node_modules
COPY --from=kontain /km /km
COPY --from=kontain /node/out/Release/node.km /node.km

CMD [ "/km", "/node.km", "/nodejsAction/app.js" ]
