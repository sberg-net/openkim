(function ($) {
    $.fn.serializeFormJSON = function () {
        var o = {};
        var a = this.serializeArray();
        $.each(a, function () {
            if (o[this.name]) {
                if (!o[this.name].push) {
                    o[this.name] = [o[this.name]];
                }
                o[this.name].push(this.value || '');
            } else {
                o[this.name] = this.value || '';
            }
        });
        return o;
    };
})(jQuery);

(function ($) {
    $.fn.serializeFiles = function () {
        var obj = $(this);
        /* ADD FILE TO PARAM AJAX */
        var formData = new FormData();
        $.each($(obj).find("input[type='file']"), function (i, tag) {
            $.each($(tag)[0].files, function (i, file) {
                formData.append(tag.name, file);
            });
        });
        var params = $(obj).serializeArray();
        $.each(params, function (i, val) {
            formData.append(val.name, val.value);
        });
        return formData;
    };
})(jQuery);

function togglePwdVisibility(id) {
    let x = document.getElementById(id);
    if (x.type === "password") {
        x.type = "text";
    } else {
        x.type = "password";
    }
}

function toggleAcivateChecked(id, elemArr) {
    const checked = $("#" + id).is(':checked');
    elemArr.forEach(elemId => {
        $('#' + elemId).prop("disabled", !checked);
    });
}

function toggleAcivateSelected(id, compValue, elemArr) {
    const selValue = $("#" + id).val();
    const disable = selValue !== compValue;
    elemArr.forEach(elemId => {
        $('#' + elemId).prop("disabled", disable);
    });
}

function showHide(elem) {
    const show = $(elem).next().attr("data-show");
    if (show === 'true') {
        $(elem).next().hide();
        $(elem).next().attr("data-show", "false");
    } else if (show === 'false') {
        $(elem).next().show();
        $(elem).next().attr("data-show", "true");
    }
}

const openkimContext = {
    toDeleteKonnektor: null,
    searchValue: ''
}

function openkimKeystoreLoeschen() {
    $("#openkim-spinner").attr("style", "");
    $.ajax({
        type: "GET",
        url: $("#konfigContainer").attr("action") + "openkimpkeystore/loeschen",
        contentType: "application/json",
        dataType: "json",
        success: function (data) {
            $("#keystore-spinner").attr("style", "display:none");
            $('#confirmDialog4DeleteOpenkimSmtpKeystore').modal('hide');
            setTimeout(konfigUebersicht, 0);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konfigError").attr("style", "");
            $("#konfigError").append(JSON.parse(jqXHR.responseText).message);
            $('#confirmDialog4DeleteOpenkimSmtpKeystore').modal('hide');
        }
    });
}

function notSelfsignedOpenkimKeystoreSpeichern() {

    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");

    const res = document.getElementById("openkimkeystoreForm").checkValidity();
    $('.needs-validation').addClass('was-validated');
    if (!res) {
        return;
    }

    $("#openkim-spinner").attr("style", "");
    const data = $("#openkimkeystoreForm").serializeFormJSON();
    $.ajax({
        type: "POST",
        url: $("#konfigContainer").attr("action") + "openkimkeystore/erstelle/notselfsigned",
        data: JSON.stringify(data),
        contentType: "application/json",
        dataType: "json",
        beforeSend: function (xhr) {
            xhr.setRequestHeader(header, token);
        },
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            setTimeout(konfigUebersicht, 0);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#openkimkeystoreFormError").attr("style", "");
            $("#openkimkeystoreFormError").empty();
            $("#openkimkeystoreFormError").append(JSON.parse(jqXHR.responseText).message);
        }
    });
}

function notSelfsignedOpenkimKeystoreErstellen() {
    $("#openkim-spinner").attr("style", "");
    $("#konfigServerStatusContainer").attr("style", "display:none");
    $("#konfigContainer").attr("style", "");
    $("#konfigContainer").empty();
    $.ajax({
        type: "GET",
        url: $("#konfigContainer").attr("action") + 'openkimkeystore/erstelle/notselfsigned',
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konfigContainer").append(data);
            document.getElementById("openkimkeystoreForm").checkValidity();
            $('.needs-validation').addClass('was-validated');
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konfigContainer").append(jqXHR.responseText);
        }
    });
}

function selfsignedOpenkimKeystoreErstellen() {
    $("#openkim-spinner").attr("style", "");
    $.ajax({
        type: "GET",
        url: $("#konfigContainer").attr("action") + "openkimkeystore/erstelle/selfsigned",
        contentType: "application/json",
        dataType: "json",
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            setTimeout(konfigUebersicht, 0);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konfigError").attr("style", "");
            $("#konfigError").append(JSON.parse(jqXHR.responseText).message);
        }
    });
}

function dashboardUebersicht() {
    $("#openkim-spinner").attr("style", "");
    $("#dashboardContainer").attr("style", "");
    $("#dashboardContainer").empty();
    $.ajax({
        type: "GET",
        url: $("#dashboardContainer").attr("action") + 'dashboard/uebersicht',
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#dashboardContainer").append(data);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#dashboardContainer").append(jqXHR.responseText);
        }
    });
}

function dashboardUebersichtAktualisieren() {
  $("#openkim-spinner").attr("style", "");
  $("#dashboardContainer").attr("style", "");
  $("#dashboardContainer").empty();
  $.ajax({
    type: "GET",
    url: $("#dashboardContainer").attr("action") + 'dashboard/uebersicht/aktualisieren',
    success: function (data) {
      $("#openkim-spinner").attr("style", "display:none");
      setTimeout(dashboardUebersicht, 0);
    },
    error: function (jqXHR, textStatus, errorThrown) {
      $("#openkim-spinner").attr("style", "display:none");
      $("#dashboardContainer").append(jqXHR.responseText);
    }
  });
}

function konfigUebersicht() {
    $("#openkim-spinner").attr("style", "");
    $("#konfigContainer").attr("style", "");
    $("#konfigContainer").empty();
    $.ajax({
        type: "GET",
        url: $("#konfigContainer").attr("action") + 'konfiguration/uebersicht',
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konfigContainer").append(data);
            setTimeout(konfigServerStatus(), 0);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konfigContainer").append(jqXHR.responseText);
        }
    });
}

function konfigServerStatus() {
    $("#openkim-spinner").attr("style", "");
    $("#konfigServerStatusContainer").attr("style", "");
    $("#konfigServerStatusContainer").empty();
    $.ajax({
        type: "GET",
        url: $("#konfigServerStatusContainer").attr("action") + 'konfiguration/serverstatus',
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konfigServerStatusContainer").append(data);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konfigServerStatusContainer").append(jqXHR.responseText);
        }
    });
}

function minimalkonfigSpeichern() {
    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");

    const res = document.getElementById("minimalkonfigForm").checkValidity();
    $('.needs-validation').addClass('was-validated');
    if (!res) {
        return;
    }

    $(".spinner-border").attr("style", "");
    const fData = $("#minimalkonfigForm").serializeFiles();
    fData.append('minimalkonfigForm', $("#logPersonalInformations").is(':checked'));

    $.ajax({
        type: "POST",
        url: $("#minimalKonfigContainer").attr("action") + "minimalkonfiguration/speichern",
        data: fData,
        cache: false,
        contentType: false,
        processData: false,
        beforeSend: function (xhr) {
            xhr.setRequestHeader(header, token);
        },
        success: function (data) {
            $(".spinner-border").attr("style", "display:none");
            setTimeout(minimalkonfigLaden, 0);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $(".spinner-border").attr("style", "display:none");
            $("#minimalkonfigFormError").attr("style", "");
            $("#minimalkonfigFormError").empty();
            $("#minimalkonfigFormError").append(JSON.parse(jqXHR.responseText).message);
        }
    });
}

function minimalkonfigLaden() {
    $("#openkim-spinner").attr("style", "");
    $("#minimalKonfigContainer").attr("style", "");
    $("#minimalKonfigContainer").empty();
    $.ajax({
        type: "GET",
        url: $("#minimalKonfigContainer").attr("action") + 'minimalkonfiguration/lade',
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#minimalKonfigContainer").append(data);
            if (document.getElementById("minimalkonfigForm")) {
                document.getElementById("minimalkonfigForm").checkValidity();
                $('.needs-validation').addClass('was-validated');
            }
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#minimalKonfigContainer").append(jqXHR.responseText);
        }
    });
}

function konfigLaden() {
    $("#openkim-spinner").attr("style", "");
    $("#konfigServerStatusContainer").attr("style", "display:none");
    $("#konfigContainer").attr("style", "");
    $("#konfigContainer").empty();
    $.ajax({
        type: "GET",
        url: $("#konfigContainer").attr("action") + 'konfiguration/lade',
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konfigContainer").append(data);
            document.getElementById("konfigForm").checkValidity();
            $('.needs-validation').addClass('was-validated');
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konfigContainer").append(jqXHR.responseText);
        }
    });
}

function konfigSpeichern() {

    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");

    const res = document.getElementById("konfigForm").checkValidity();
    $('.needs-validation').addClass('was-validated');
    if (!res) {
        return;
    }

    $(".spinner-border").attr("style", "");
    const fData = $("#konfigForm").serializeFiles();
    fData.append('logPersonalInformations', $("#logPersonalInformations").is(':checked'));
    fData.append('logKonnektorExecute', $("#logKonnektorExecute").is(':checked'));
    fData.append('writeSmtpCmdLogFile', $("#writeSmtpCmdLogFile").is(':checked'));
    fData.append('writePop3CmdLogFile', $("#writePop3CmdLogFile").is(':checked'));

    $.ajax({
        type: "POST",
        url: $("#konfigContainer").attr("action") + "konfiguration/speichern",
        data: fData,
        cache: false,
        contentType: false,
        processData: false,
        beforeSend: function (xhr) {
            xhr.setRequestHeader(header, token);
        },
        success: function (data) {
            $(".spinner-border").attr("style", "display:none");
            setTimeout(konfigUebersicht, 0);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $(".spinner-border").attr("style", "display:none");
            $("#konfigFormError").attr("style", "");
            $("#konfigFormError").empty();
            $("#konfigFormError").append(JSON.parse(jqXHR.responseText).message);
        }
    });
}

function konnektorLaden(uuid, refeshConfig) {
    $("#openkim-spinner").attr("style", "");

    $("#konfigServerStatusContainer").empty();
    $("#konfigServerStatusContainer").attr("style", "display:none");

    $("#vzdEintragUebersicht").empty();
    $("#vzdEintragUebersicht").attr("style", "display:none");

    $("#konnWebserviceUebersicht").empty();
    $("#konnWebserviceUebersicht").attr("style", "display:none");

    $("#ntpUebersicht").empty();
    $("#ntpUebersicht").attr("style", "display:none");

    $("#konfigContainer").attr("style", "");
    $("#konfigContainer").empty();

    const refresh = (refeshConfig === undefined) ? false : refeshConfig;

    $.ajax({
        type: "GET",
        url: $("#konfigContainer").attr("action") + 'konnektor/lade/' + uuid + '/' + refresh,
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konfigContainer").append(data);
            document.getElementById("konnektorForm").checkValidity();
            $('.needs-validation').addClass('was-validated');
            toggleAcivateChecked('activated', ['ip', 'name', 'timeout', 'konnektorAuthMethod', 'certAuthPwd', 'certAuthPwdCheck', 'certFilename', 'basicAuthUser', 'basicAuthPwd', 'basicAuthPwdCheck']);
            toggleAcivateSelected('konnektorAuthMethod', 'CERT', ['certAuthPwd', 'certAuthPwdCheck', 'certFilename']);
            toggleAcivateSelected('konnektorAuthMethod', 'BASICAUTH', ['basicAuthUser', 'basicAuthPwd', 'basicAuthPwdCheck']);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konfigContainer").append(jqXHR.responseText);
        }
    });
}

function konnektorLoeschen() {
    $("#konnektorTableError").attr("style", "display:none");
    $("#openkim-spinner").attr("style", "");
    $.ajax({
        type: "GET",
        url: $("#konfigContainer").attr("action") + "konnektor/loeschen/" + openkimContext.toDeleteKonnektor,
        contentType: "application/json",
        dataType: "json",
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $('#confirmDialog4DeleteKonnektor').modal('hide');
            setTimeout(konfigUebersicht, 0);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konnektorTableError").attr("style", "");
            $("#konnektorTableError").append(JSON.parse(jqXHR.responseText).message);
            $('#confirmDialog4DeleteKonnektor').modal('hide');
        }
    });
}

function konnektorSpeichern() {
    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");

    const res = document.getElementById("konnektorForm").checkValidity();
    $('.needs-validation').addClass('was-validated');
    if (!res) {
        return;
    }

    $(".spinner-border").attr("style", "");
    const fData = $("#konnektorForm").serializeFiles();
    fData.append('activated', $("#activated").is(':checked'));

    $.ajax({
        type: "POST",
        url: $("#konfigContainer").attr("action") + "konnektor/speichern",
        data: fData,
        cache: false,
        contentType: false,
        processData: false,
        beforeSend: function (xhr) {
            xhr.setRequestHeader(header, token);
        },
        success: function (data) {
            $(".spinner-border").attr("style", "display:none");
            setTimeout(konfigUebersicht, 0);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $(".spinner-border").attr("style", "display:none");
            $("#konnektorFormError").attr("style", "");
            $("#konnektorFormError").empty();
            $("#konnektorFormError").append(JSON.parse(jqXHR.responseText).message);
        }
    });
}

function logUebersicht(typ, id) {
    $("#openkim-spinner").attr("style", "");
    $("#logContainer").attr("style", "");
    $("#logContainer").empty();

    openkimContext.searchValue = id ? id : '';

    $.ajax({
        type: "GET",
        url: $("#logContainer").attr("action") + 'log/uebersicht/' + typ + ((id !== undefined) ? '/' + id : ''),
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#logContainer").append(data);
            $("#search").val(openkimContext.searchValue);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#logContainer").append(jqXHR.responseText);
        }
    });
}

function vzdUebersichtClose() {
    $("#konfigContainer").attr("style", "");
    $("#vzdEintragUebersicht").empty();
}

function vzdUebersicht(konnId) {

    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");

    $("#openkim-spinner").attr("style", "");
    $("#konfigContainer").attr("style", "display:none");

    $("#vzdEintragUebersicht").attr("style", "");

    const searchValue = ($("#search").length !== 0) ? $("#search").val() : '';
    const resultWithCertificates = $("#searchWithCerts").is(':checked');
    $("#vzdEintragUebersicht").empty();

    $.ajax({
        type: "POST",
        url: $("#vzdEintragUebersicht").attr("action") + 'vzd/suchen/' + konnId + '/' + resultWithCertificates,
        data: 'searchValue=' + searchValue,
        beforeSend: function (xhr) {
            xhr.setRequestHeader(header, token);
        },
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#vzdEintragUebersicht").append(data);
            if ($("#search").length !== 0) {
                $("#search").val(searchValue);
            }
            $("#searchWithCerts").prop('checked', resultWithCertificates);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#vzdEintragUebersicht").append(jqXHR.responseText);
        }
    });
}

function dnsUebersichtClose() {
    $("#konfigContainer").attr("style", "");
    $("#dnsEintragUebersicht").empty();
}

function dnsUebersicht(konnId) {

    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");

    $("#openkim-spinner").attr("style", "");
    $("#konfigContainer").attr("style", "display:none");

    $("#dnsEintragUebersicht").attr("style", "");

    const searchValue = ($("#domain").length !== 0) ? $("#domain").val() : '';
    const recordType = ($("#recordType").length !== 0) ? $("#recordType").val() : 'A';
    $("#dnsEintragUebersicht").empty();

    $.ajax({
        type: "POST",
        url: $("#dnsEintragUebersicht").attr("action") + 'dns/testen/' + konnId + "/" + recordType,
        data: 'domain=' + searchValue,
        beforeSend: function (xhr) {
            xhr.setRequestHeader(header, token);
        },
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#dnsEintragUebersicht").append(data);
            if ($("#domain").length !== 0) {
                $("#domain").val(searchValue);
            }
            if ($("#recordType").length !== 0) {
                $("#recordType").val(recordType);
            }
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#dnsEintragUebersicht").append(jqXHR.responseText);
        }
    });
}

function konnWebserviceUebersicht(konnId, wsId) {
    $("#openkim-spinner").attr("style", "");
    $("#konfigContainer").attr("style", "display:none");

    $("#konnWebserviceUebersicht").empty();
    $("#konnWebserviceUebersicht").attr("style", "");

    $.ajax({
        type: "GET",
        url: $("#konnWebserviceUebersicht").attr("action") + 'konnwebservice/uebersicht/' + konnId + '/' + wsId,
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konnWebserviceUebersicht").append(data);
        },
        error: function (jqXHR) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konnWebserviceUebersicht").append(jqXHR.responseText);
        }
    });
}

function verifyPinUebersicht(konnId, wsId, opId, pinTyp, cardHandle) {
    $("#openkim-spinner").attr("style", "");
    $("#konfigContainer").attr("style", "display:none");

    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");

    $("#konnWebserviceUebersicht").empty();
    $("#konnWebserviceUebersicht").attr("style", "");

    var data = {
        konnId: konnId,
        wsId: wsId,
        opId: opId,
        pinTyp: pinTyp,
        cardHandle: cardHandle,
        testMode: false
    };

    $.ajax({
        type: "POST",
        url: $("#konnWebserviceUebersicht").attr("action") + 'konnwebservice/uebersicht',
        data: JSON.stringify(data),
        beforeSend: function (xhr) {
            xhr.setRequestHeader(header, token);
        },
        contentType: "application/json",
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konnWebserviceUebersicht").append(data);
        },
        error: function (jqXHR) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#konnWebserviceUebersicht").append(jqXHR.responseText);
        }
    });
}

function konnWebserviceAusfuehren(formId) {

    $("#" + formId + "Result").attr("style", "display:none");
    $("#" + formId + "Result").empty();
    $("#" + formId + "Error").attr("style", "display:none");
    $("#" + formId + "Error").empty();

    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");

    const res = document.getElementById(formId).checkValidity();
    $(formId + ' .needs-validation').addClass('was-validated');
    if (!res) {
        return;
    }

    $("#" + formId + " .spinner-border").attr("style", "");
    var data = {};
    if (formId !== undefined) {
        data = $("#" + formId).serializeFormJSON();
    }
    $.ajax({
        type: "POST",
        url: $("#konnWebserviceUebersicht").attr("action") + 'konnwebservice/ausfuehren',
        data: JSON.stringify(data),
        beforeSend: function (xhr) {
            xhr.setRequestHeader(header, token);
        },
        contentType: "application/json",
        success: function (data) {
            $("#" + formId + " .spinner-border").attr("style", "display:none");
            $("#" + formId + "Result").attr("style", "");
            $("#" + formId + "Result").append(data);
        },
        error: function (jqXHR) {
            $("#" + formId + " .spinner-border").attr("style", "display:none");
            $("#" + formId + "Error").attr("style", "");
            $("#" + formId + "Error").append(jqXHR.responseText);
        }
    });
}

function ntpUebersichtClose() {
    $("#konfigContainer").attr("style", "");
    $("#ntpUebersicht").empty();
}

function ntpUebersicht(konnId) {

    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");

    $("#openkim-spinner").attr("style", "");
    $("#konfigContainer").attr("style", "display:none");

    $("#ntpUebersicht").empty();
    $("#ntpUebersicht").attr("style", "");

    $.ajax({
        type: "POST",
        url: $("#ntpUebersicht").attr("action") + 'ntp/testen/' + konnId,
        beforeSend: function (xhr) {
            xhr.setRequestHeader(header, token);
        },
        success: function (data) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#ntpUebersicht").append(data);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $("#openkim-spinner").attr("style", "display:none");
            $("#ntpUebersicht").append(jqXHR.responseText);
        }
    });
}

function pipelineoperationtestLaden() {
    $.ajax({
        type: "GET",
        url: $("#pipelineoperationtestContainer").attr("action") + "pipelineoperationtest/uebersicht",
        success: function (data) {
            $(".spinner-border").attr("style", "display:none");
            $("#pipelineoperationtestContainer").empty();
            $("#pipelineoperationtestContainer").append(data);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            $(".spinner-border").attr("style", "display:none");
            $("#pipelineoperationtestContainer").empty();
            $("#pipelineoperationtestContainer").append(JSON.parse(jqXHR.responseText).message);
        }
    });
}

function pipelineoperationtestOpLaden(operationKey) {
  if (operationKey === 'unknown') {
    $("#pipelineoperationtestOpContainer").empty();
  }
  else {
    $.ajax({
      type: "GET",
      url: $("#pipelineoperationtestOpContainer").attr("action") + "pipelineoperationtest/lade/"+operationKey,
      success: function (data) {
        $(".spinner-border").attr("style", "display:none");
        $("#pipelineoperationtestOpContainer").empty();
        $("#pipelineoperationtestOpContainer").append(data);
      },
      error: function (jqXHR, textStatus, errorThrown) {
        $(".spinner-border").attr("style", "display:none");
        $("#pipelineoperationtestOpContainer").empty();
        $("#pipelineoperationtestOpContainer").append(JSON.parse(jqXHR.responseText).message);
      }
    });
  }
}

function pipelineoperationtestOpAusfuehren() {
  const token = $("meta[name='_csrf']").attr("content");
  const header = $("meta[name='_csrf_header']").attr("content");

  let fData = new FormData();
  if ($('#opForm').length > 0) {
    const res = document.getElementById("opForm").checkValidity();
    $('.needs-validation').addClass('was-validated');
    if (!res) {
      return;
    }

    $(".spinner-border").attr("style", "");
    fData = $("#opForm").serializeFiles();
  }
  else {
    return;
  }

  $("#opFormResult").attr("style", "display:none");
  $("#opFormResult").empty();

  $("#opFormError").attr("style", "display:none");
  $("#opFormError").empty();

  $.ajax({
    type: "POST",
    url: $("#pipelineoperationtestOpContainer").attr("action") + "pipelineoperationtest/execute",
    data: fData,
    cache: false,
    contentType: false,
    processData: false,
    beforeSend: function (xhr) {
      xhr.setRequestHeader(header, token);
    },
    success: function (data) {
      $(".spinner-border").attr("style", "display:none");
      $("#opFormResult").attr("style", "");
      $("#opFormResult").empty();
      $("#opFormResult").append(data);
    },
    error: function (jqXHR, textStatus, errorThrown) {
      $(".spinner-border").attr("style", "display:none");
      $("#opFormError").attr("style", "");
      $("#opFormError").empty();
      $("#opFormError").append(JSON.parse(jqXHR.responseText).message);
    }
  });
}

function changeUserPwd() {
  const token = $("meta[name='_csrf']").attr("content");
  const header = $("meta[name='_csrf_header']").attr("content");

  const res = document.getElementById("changePwdForm").checkValidity();
  $('.needs-validation').addClass('was-validated');
  if (!res) {
    $('#confirmDialog4ChangePwd').modal('hide');
    return;
  }

  $("#openkim-spinner").attr("style", "");

  const data = $("#changePwdForm").serializeFormJSON();

  $.ajax({
    type: "POST",
    url: $("#changePwdFormContainer").attr("action")+"user/changePwd",
    data: JSON.stringify(data),
    contentType: "application/json",
    dataType: "json",
    beforeSend: function(xhr) {
      xhr.setRequestHeader(header, token);
    },
    success: function() {
      $("#openkim-spinner").attr("style", "display:none");
      $('#confirmDialog4ChangePwd').modal('hide');
      document.getElementById('logout-form').submit();
    },
    error: function(jqXHR,textStatus,errorThrown) {
      $("#openkim-spinner").attr("style", "display:none");
      $('#confirmDialog4ChangePwd').modal('hide');
      $("#changePwdFormError").attr("style", "");
      $("#changePwdFormError").empty();
      $("#changePwdFormError").append(JSON.parse(jqXHR.responseText).message);
    }
  });
}
