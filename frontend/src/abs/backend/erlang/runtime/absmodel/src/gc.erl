%% This file is licensed under the terms of the Modified BSD License.
-module(gc).
%% The garbage collection module
%% For now implemented as a registered process running globally
%% This should probably be changed if we want one process per COG

-include_lib("abs_types.hrl").
-export([start/2, stop/0, init/2, extract_references/1, get_references/1]).
-export([register_future/1, unroot_future/1]).
-export([register_cog/1, unregister_cog/1]).
-export([register_object/1]).

-export([behaviour_info/1]).

-undef(MIN_PROC_FACTOR).
-undef(MAX_PROC_FACTOR).
-undef(MAX_COLLECTION_INTERVAL).
-undef(MIN_THRESH).
-undef(RED_THRESH).
-undef(INC_THRESH).

-define(MIN_PROC_FACTOR, 0.5).
-define(MAX_PROC_FACTOR, 0.9).
-define(MAX_COLLECTION_INTERVAL, 100).          % collect every 0.1 seconds

-define(MIN_THRESH, 16).
-define(RED_THRESH, 0.25).
-define(INC_THRESH, 0.75).

-record(state, {cogs=gb_sets:empty(),objects=gb_sets:empty(),
                futures=gb_sets:empty(),root_futures=gb_sets:empty(),
                previous=erlang:monotonic_time(milli_seconds),
                limit=?MIN_THRESH, proc_factor=?MIN_PROC_FACTOR, log=false,
                debug=false}).

behaviour_info(callbacks) ->
    [{get_references, 1}].

start(Log, Debug) ->
    register(gc, spawn(?MODULE, init, [Log, Debug])).

stop() ->
    gc!stop.

init(Log, Debug) ->
    loop(#state{log=Log, debug=Debug}).

gcstats(Log, Statistics) -> 
    case Log of
        true -> io:format("~p.~n",[{gcstats, erlang:monotonic_time(milli_seconds), Statistics}]);
        false -> ok
    end.


register_future(Fut) ->
    %% Fut is the plain pid of the Future process
    gc!{Fut, self()},
    receive
        {gc, ok} -> Fut
    end.

unroot_future(Fut) ->
    %% Fut is the plain pid of the Future process
    gc!{unroot, Fut}.

register_cog(Cog) ->
    %% Cog is a #cog record
    gc!{Cog, self()},
    receive
        {gc, ok} -> Cog
    end.

unregister_cog(Cog) ->
    %% Cog is the plain pid of the Cog process
    gc!{die, Cog}.

register_object(Obj) ->
    %% Obj is an #object record
    gc!Obj.


loop(State=#state{cogs=Cogs, objects=Objects, futures=Futures, root_futures=RootFutures, log=Log}) ->
    gcstats(Log, {{memory, erlang:memory()}, {cogs, gb_sets:size(Cogs)}, {objects, gb_sets:size(Objects)},
              {futures, gb_sets:size(Futures), gb_sets:size(RootFutures)},
              {processes, erlang:system_info(process_count), erlang:system_info(process_limit)}}),
    NewState =
        receive
            {#cog{ref=Ref}, Sender} ->
                Ref ! Sender ! {gc, ok},
                State#state{cogs=gb_sets:insert({cog, Ref}, Cogs)};
            #object{ref=Ref} ->
                State#state{objects=gb_sets:insert({object, Ref}, Objects)};
            {Ref, Sender} when is_pid(Ref) ->
                Sender ! {gc, ok},
                State#state{root_futures=gb_sets:insert({future, Ref}, RootFutures)};
            {die, Cog} ->
                State#state{cogs=gb_sets:delete({cog, Cog}, Cogs)};
            {unroot, Sender} ->
                State#state{futures=gb_sets:insert({future, Sender}, Futures),
                            root_futures=gb_sets:delete({future, Sender}, RootFutures)};
            stop ->
                unregister(gc),
                stop
        end,
    case is_collection_needed(NewState) of
        stop -> ok;
        true ->
            gcstats(Log, stop_world),
            gb_sets:fold(fun ({cog, Ref}, ok) -> cog:stop_world(Ref) end, ok, NewState#state.cogs),
            await_stop(NewState, 0);
        false ->
            loop(NewState)
    end.

await_stop(State=#state{cogs=Cogs,objects=Objects,futures=Futures,root_futures=RootFutures, log=Log},Stopped) ->
    gcstats(Log, {{memory, erlang:memory()}, {cogs, gb_sets:size(Cogs)}, {objects, gb_sets:size(Objects)},
              {futures, gb_sets:size(Futures), gb_sets:size(RootFutures)},
              {processes, erlang:system_info(process_count), erlang:system_info(process_limit)}}),
    {NewState, NewStopped} =
        receive
            {#cog{ref=Ref}, Sender} ->
                cog:stop_world(Ref),
                Sender ! {gc, ok},
                {State#state{cogs=gb_sets:insert({cog, Ref}, Cogs)}, Stopped};
            #object{ref=Ref} ->
                {State#state{objects=gb_sets:insert({object, Ref}, Objects)}, Stopped};
            {Ref, Sender} when is_pid(Ref) ->
                Sender ! {gc, ok},
                {State#state{root_futures=gb_sets:insert({future, Ref}, RootFutures)}, Stopped};
            {die, Cog} ->
                {State#state{cogs=gb_sets:delete({cog, Cog}, Cogs)}, Stopped};
            {unroot, Sender} ->
                {State#state{futures=gb_sets:insert({future, Sender}, Futures),
                             root_futures=gb_sets:delete({future, Sender}, RootFutures)}, Stopped};
            {stopped, _Ref} ->
                {State, Stopped + 1}
        end,
    NewCogs = NewState#state.cogs,
    case NewStopped >= gb_sets:size(NewCogs) of
        true ->
            gcstats(Log, mark),
            Black=mark([], ordsets:from_list(gb_sets:to_list(gb_sets:union(NewCogs, NewState#state.root_futures)))),
            gcstats(Log, sweep),
            StateAfterSweep=sweep(NewState, gb_sets:from_ordset(Black)),
            loop(StateAfterSweep);
        false -> await_stop(NewState,NewStopped)
    end.

mark(Black, []) ->
    Black;
mark(Black, Gray) ->
    NewBlack = ordsets:union(Black, Gray),
    NewGray = ordsets:subtract(ordsets:union(ordsets:from_list(rpc:pmap({gc, get_references}, [], Gray))), Black),
    mark(NewBlack, NewGray).

sweep(State=#state{cogs=Cogs,objects=Objects,futures=Futures,
                   limit=Lim, proc_factor=PFactor, log=Log},Black) ->
    WhiteObjects = gb_sets:subtract(Objects, Black),
    WhiteFutures = gb_sets:subtract(Futures, Black),
    BlackObjects = gb_sets:intersection(Objects, Black),
    BlackFutures = gb_sets:intersection(Futures, Black),
    gcstats(Log,{sweep, {objects, gb_sets:size(WhiteObjects), gb_sets:size(BlackObjects)},
             {futures, gb_sets:size(WhiteFutures), gb_sets:size(BlackFutures)}}),
    gb_sets:fold(fun ({object, Ref}, ok) -> object:die(Ref, gc), ok end, ok, WhiteObjects),
    gb_sets:fold(fun ({future, Ref}, ok) -> future:die(Ref, gc), ok end, ok, WhiteFutures),
    gcstats(Log,resume_world),
    gb_sets:fold(fun ({cog, Ref}, ok) -> cog:resume_world(Ref) end, ok, Cogs),
    Count = gb_sets:size(BlackObjects) + gb_sets:size(BlackFutures),
    NewLim = if Count > Lim * ?INC_THRESH -> Lim * 2;
                Count < Lim * ?RED_THRESH -> max(Lim div 2, ?MIN_THRESH);
                true -> Lim
             end,
    ProcessCount = erlang:system_info(process_count),
    NewPFactor = if ProcessCount > PFactor -> min(PFactor + 0.05, ?MAX_PROC_FACTOR);
                    PFactor > ?MIN_PROC_FACTOR -> PFactor - 0.05;
                    true -> PFactor
                 end,
    State#state{objects=BlackObjects, futures=BlackFutures, previous=erlang:monotonic_time(milli_seconds),
                     limit=NewLim, proc_factor=PFactor}.

get_references({Module, Ref}) ->
    Module:get_references(Ref).

is_collection_needed(stop) ->
    stop;
is_collection_needed(State=#state{objects=Objects,futures=Futures,
                                  previous=PTime,limit=Lim,proc_factor=PFactor,
                                  debug=Debug}) ->
    Debug
    orelse (erlang:monotonic_time(milli_seconds) - PTime) > ?MAX_COLLECTION_INTERVAL
    orelse erlang:system_info(process_count) / erlang:system_info(process_limit) > PFactor.


extract_references(DataStructure) ->
    ordsets:from_list(lists:flatten([to_deep_list(DataStructure)])).

to_deep_list(#object{ref=Ref}) ->
    {object, Ref};
to_deep_list(#cog{}) ->
    [];
to_deep_list(Ref) when is_pid(Ref) ->
    {future, Ref};
to_deep_list(DataStructure) when is_tuple(DataStructure) ->
    lists:map(fun to_deep_list/1, tuple_to_list(DataStructure));
to_deep_list(List) when is_list(List) ->
    lists:map(fun to_deep_list/1, List);
to_deep_list(FlatData) ->
    [].
