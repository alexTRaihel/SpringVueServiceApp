import Vue from 'vue'

const comments = Vue.resource('/comments{/id}');

export default {
    add: comment => comments.save({}, comment)
}