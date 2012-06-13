(function() {

  var prefs = new gadgets.Prefs();

  var Constants = {
    getLikeStatusOperationId: 'Services.GetLikeStatus',
    likeOperationId: 'Services.Like',
    cancelLikeOperationId: 'Services.CancelLike'
  };

  var docId = prefs.getString("docId") || "";
  var activityId = prefs.getString("activityId") || "";

  var likeStatus;

  gadgets.util.registerOnLoadHandler(function() {
    loadLikeStatus();
  });

  function loadLikeStatus() {
    var NXRequestParams= { operationId : Constants.getLikeStatusOperationId,
      operationParams: {
        document: docId,
        activityId: activityId
      },
      operationContext: {},
      operationCallback: storeLikeStatus
    };

    doAutomationRequest(NXRequestParams);
  }

  function storeLikeStatus(response, params) {
    likeStatus = response.data;
    displayLikeStatus();
  }

  function displayLikeStatus() {
    var html = '';
    html += '<div class="likeStatus">';
    var alt = '';
    if (likeStatus.userLikeStatus == 1) {
      if (likeStatus.likesCount == 1) {
        alt = prefs.getMsg('gadget.label.you.liked.document');
      } else {
        alt = prefs.getMsg('gadget.label.you.and.other.liked.document1') + ' ' + (likeStatus.likesCount - 1) + ' ';
        alt += prefs.getMsg('gadget.label.you.and.other.liked.document2')
      }
      html += '<img class="likeIcon jsLikeIcon" src="' + NXGadgetContext.clientSideBaseUrl + 'icons/vote_up_active.png" alt="' + alt + '" title="' + alt + '"></img>';
    } else {
      alt = likeStatus.likesCount + ' ' + prefs.getMsg('gadget.label.people.liked.document');
      alt += ' ';
      alt += prefs.getMsg('gadget.label.like.what.about.you');
      html += '<img class="likeIcon jsLikeIcon" src="' + NXGadgetContext.clientSideBaseUrl + 'icons/vote_up_unactive.png" alt="' + alt + '" title="' + alt + '"></img>';
    }
    html += '<span class="likesCount">' + likeStatus.likesCount + ' ' + prefs.getMsg('gadget.label.people.liked.document') + '</span>';
    html += '</div>';
    jQuery('#content').html(html);
    gadgets.window.adjustHeight();

    jQuery('.jsLikeIcon').click(function() {
      var operationId = "";
      if (likeStatus.userLikeStatus == 1) {
        operationId = Constants.cancelLikeOperationId;
      } else {
        operationId = Constants.likeOperationId;
      }
      var NXRequestParams= { operationId : operationId,
        operationParams: {
          document: docId,
          activityId: activityId
        },
        operationContext: {},
        operationCallback: storeLikeStatus
      };
      doAutomationRequest(NXRequestParams);
    });
  }
}());
